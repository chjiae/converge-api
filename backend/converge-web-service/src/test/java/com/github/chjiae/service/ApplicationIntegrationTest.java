package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户申请接口集成测试，覆盖提交申请、查询、审核通过/拒绝等流程。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApplicationIntegrationTest extends BaseIntegrationTest {

    static String adminToken;
    static Long approvedAppId;
    static Long rejectedAppId;
    static Long createdTenantIdFromApp;

    @Test
    @Order(1)
    void setup_获取超管令牌() {
        adminToken = login("admin", "admin123");
    }

    @Test
    @Order(2)
    void submitApplication_REGISTER类型_提交成功() {
        String body = """
                {
                  "companyName": "测试公司A",
                  "contactName": "张三",
                  "contactEmail": "zhangsan@companyA.com",
                  "contactPhone": "13800138001",
                  "description": "申请试用",
                  "applicationType": "TRIAL",
                  "adminUsername": "companyA_admin",
                  "adminEmail": "admin@companyA.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> response = postPublic("/api/v1/applications", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("companyName").asText()).isEqualTo("测试公司A");
        assertThat(data.get("status").asText()).isEqualTo("PENDING");
        approvedAppId = data.get("id").asLong();
    }

    @Test
    @Order(3)
    void submitApplication_TRIAL类型_提交成功() {
        String body = """
                {
                  "companyName": "测试公司B",
                  "contactName": "李四",
                  "contactEmail": "lisi@companyB.com",
                  "description": "申请注册充值",
                  "applicationType": "REGISTER",
                  "adminUsername": "companyB_admin",
                  "adminEmail": "admin@companyB.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> response = postPublic("/api/v1/applications", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("applicationType").asText()).isEqualTo("REGISTER");
        rejectedAppId = data.get("id").asLong();
    }

    @Test
    @Order(4)
    void getApplication_公开查询_返回详情() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/v1/applications/" + approvedAppId, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("id").asLong()).isEqualTo(approvedAppId);
        assertThat(data.get("companyName").asText()).isEqualTo("测试公司A");
    }

    @Test
    @Order(5)
    void listApplications_超管查询_返回分页() {
        ResponseEntity<String> response = get("/api/v1/applications?page=1&size=10", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("list").isArray()).isTrue();
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(6)
    void approveApplication_审核通过_创建租户和管理员() {
        ResponseEntity<String> response = post(
                "/api/v1/applications/" + approvedAppId + "/approve", adminToken, "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("status").asText()).isEqualTo("APPROVED");

        // 审核通过后应能用管理员账号登录
        String tenantAdminToken = login("companyA_admin", "test123456");
        assertThat(tenantAdminToken).isNotBlank();
    }

    @Test
    @Order(7)
    void rejectApplication_审核拒绝_附带原因() {
        String body = "{\"rejectReason\":\"信息不完整，请补充材料\"}";
        ResponseEntity<String> response = post(
                "/api/v1/applications/" + rejectedAppId + "/reject", adminToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("status").asText()).isEqualTo("REJECTED");
        assertThat(data.get("rejectReason").asText()).contains("信息不完整");
    }

    @Test
    @Order(8)
    void submitApplication_缺少必填字段_校验失败() {
        String body = "{\"companyName\":\"\",\"contactName\":\"\",\"contactEmail\":\"\",\"applicationType\":\"REGISTER\",\"adminUsername\":\"\",\"adminEmail\":\"\",\"adminPassword\":\"\"}";
        ResponseEntity<String> response = postPublic("/api/v1/applications", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
