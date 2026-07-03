package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.chjiae.service.entity.Subscription;
import com.github.chjiae.service.entity.Tenant;
import com.github.chjiae.service.mapper.SubscriptionMapper;
import com.github.chjiae.service.mapper.TenantMapper;
import com.github.chjiae.service.scheduler.TenantExpiryScheduler;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 定时任务集成测试，覆盖租户到期扫描和订阅到期扫描场景。
 * 通过直接注入 TenantExpiryScheduler 调用定时方法，验证业务逻辑正确性。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchedulerIntegrationTest extends BaseIntegrationTest {

    /** 超管令牌 */
    static String adminToken;

    /** 即将到期租户 ID（7 天内到期，应触发预警通知） */
    static Long expiringTenantId;

    /** 已过期租户 ID（到期时间已过，应被标记为 EXPIRED） */
    static Long expiredTenantId;

    /** 即将到期租户的管理员令牌 */
    static String expiringTenantAdminToken;

    /** 已过期租户的管理员令牌 */
    static String expiredTenantAdminToken;

    /** 到期订阅对应的订阅 ID */
    static Long expiredSubscriptionId;

    @Autowired
    private TenantExpiryScheduler tenantExpiryScheduler;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private SubscriptionMapper subscriptionMapper;

    @Test
    @Order(1)
    void setup_创建测试租户和订阅数据() {
        adminToken = login("admin", "admin123");

        // 创建即将到期的租户（7 天内到期）
        String expiringTenantBody = """
                {
                  "code": "sched_expiring",
                  "name": "即将到期测试租户",
                  "description": "定时任务测试-即将到期",
                  "adminUsername": "sched_exp_admin",
                  "adminEmail": "sched_exp@test.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> resp1 = post("/api/v1/tenants", adminToken, expiringTenantBody);
        JsonNode data1 = assertSuccess(resp1);
        expiringTenantId = data1.get("id").asLong();
        expiringTenantAdminToken = login("sched_exp_admin", "test123456");

        // 创建已过期的租户
        String expiredTenantBody = """
                {
                  "code": "sched_expired",
                  "name": "已过期测试租户",
                  "description": "定时任务测试-已过期",
                  "adminUsername": "sched_expd_admin",
                  "adminEmail": "sched_expd@test.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> resp2 = post("/api/v1/tenants", adminToken, expiredTenantBody);
        JsonNode data2 = assertSuccess(resp2);
        expiredTenantId = data2.get("id").asLong();
        expiredTenantAdminToken = login("sched_expd_admin", "test123456");

        // 为已过期租户创建一个 ACTIVE 状态的订阅（到期日期在过去）
        // 先创建 PENDING 订阅，再标记为已支付
        String subBody = """
                {
                  "tenantId": %d,
                  "planType": "MONTHLY",
                  "amount": 99.00,
                  "startDate": "2025-01-01",
                  "endDate": "2025-02-01",
                  "paymentMethod": "OFFLINE",
                  "remark": "定时任务测试-到期订阅"
                }
                """.formatted(expiredTenantId);
        ResponseEntity<String> subResp = post("/api/v1/subscriptions", adminToken, subBody);
        JsonNode subData = assertSuccess(subResp);
        Long subId = subData.get("id").asLong();

        // 标记为已支付，使订阅变为 ACTIVE
        ResponseEntity<String> payResp = put("/api/v1/subscriptions/" + subId + "/pay", adminToken, "{}");
        assertSuccess(payResp);

        // 手动将订阅的 endDate 设为过去，模拟到期场景
        Subscription subscription = subscriptionMapper.selectById(subId);
        subscription.setEndDate(LocalDate.now().minusDays(1));
        subscriptionMapper.updateById(subscription);
        expiredSubscriptionId = subId;
    }

    @Test
    @Order(2)
    void setup_设置租户到期时间() {
        // 将即将到期租户的 expiredAt 设为 7 天后（触发 WARNING_DAYS 阈值）
        Tenant expiringTenant = tenantMapper.selectById(expiringTenantId);
        expiringTenant.setExpiredAt(LocalDateTime.now().plusDays(7));
        tenantMapper.updateById(expiringTenant);

        // 将已过期租户的 expiredAt 设为过去（已到期）
        Tenant expiredTenant = tenantMapper.selectById(expiredTenantId);
        expiredTenant.setExpiredAt(LocalDateTime.now().minusDays(1));
        tenantMapper.updateById(expiredTenant);
    }

    @Test
    @Order(3)
    void scanExpiringTenants_即将到期租户_应创建预警通知() {
        // 直接调用定时任务方法
        tenantExpiryScheduler.scanExpiringTenants();

        // 验证即将到期租户状态仍为 ACTIVE（未到期不应变更状态）
        ResponseEntity<String> tenantResp = get("/api/v1/tenants/" + expiringTenantId, adminToken);
        JsonNode tenantData = assertSuccess(tenantResp);
        assertThat(tenantData.get("status").asText()).isEqualTo("ACTIVE");

        // 验证通知已创建（以即将到期租户管理员身份查询通知）
        ResponseEntity<String> notifResp = get("/api/v1/notifications?page=1&size=10", expiringTenantAdminToken);
        JsonNode notifData = assertSuccess(notifResp);
        assertThat(notifData.get("total").asLong())
                .as("应至少创建一条到期预警通知")
                .isGreaterThanOrEqualTo(1);

        // 验证通知标题包含到期提醒关键词
        JsonNode notifList = notifData.get("list");
        boolean hasExpiryWarning = false;
        for (JsonNode notif : notifList) {
            if (notif.get("title").asText().contains("到期提醒")) {
                hasExpiryWarning = true;
                break;
            }
        }
        assertThat(hasExpiryWarning).as("应存在到期预警通知").isTrue();
    }

    @Test
    @Order(4)
    void scanExpiringTenants_已到期租户_状态应变为EXPIRED() {
        // 直接调用定时任务方法
        tenantExpiryScheduler.scanExpiringTenants();

        // 验证已过期租户状态已更新为 EXPIRED
        ResponseEntity<String> tenantResp = get("/api/v1/tenants/" + expiredTenantId, adminToken);
        JsonNode tenantData = assertSuccess(tenantResp);
        assertThat(tenantData.get("status").asText())
                .as("已到期租户状态应为 EXPIRED")
                .isEqualTo("EXPIRED");
    }

    @Test
    @Order(5)
    void scanExpiredSubscriptions_到期订阅_状态应变为EXPIRED() {
        // 直接调用订阅到期扫描方法
        tenantExpiryScheduler.scanExpiredSubscriptions();

        // 验证订阅状态已更新为 EXPIRED（通过全平台订阅列表查询）
        ResponseEntity<String> subResp = get("/api/v1/subscriptions?page=1&size=100", adminToken);
        JsonNode subData = assertSuccess(subResp);

        // 查找目标订阅记录
        JsonNode list = subData.get("list");
        boolean found = false;
        for (JsonNode sub : list) {
            if (sub.get("id").asLong() == expiredSubscriptionId) {
                assertThat(sub.get("status").asText())
                        .as("到期订阅状态应为 EXPIRED")
                        .isEqualTo("EXPIRED");
                found = true;
                break;
            }
        }
        assertThat(found).as("应找到目标订阅记录").isTrue();
    }
}
