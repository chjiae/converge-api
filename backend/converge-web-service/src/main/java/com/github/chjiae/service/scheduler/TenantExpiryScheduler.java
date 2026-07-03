package com.github.chjiae.service.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.chjiae.common.enums.NotificationType;
import com.github.chjiae.common.enums.SubscriptionStatus;
import com.github.chjiae.common.enums.TenantStatus;
import com.github.chjiae.service.entity.Subscription;
import com.github.chjiae.service.entity.Tenant;
import com.github.chjiae.service.mapper.SubscriptionMapper;
import com.github.chjiae.service.mapper.TenantMapper;
import com.github.chjiae.service.service.NotificationService;
import com.github.chjiae.service.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 租户到期扫描定时任务。
 * <p>
 * 负责定期检查租户和订阅的到期情况，并发送相应的提醒通知。
 * 使用 Redis 分布式锁防止多实例部署时重复执行。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExpiryScheduler {

    /** 租户数据访问层 */
    private final TenantMapper tenantMapper;

    /** 订阅数据访问层 */
    private final SubscriptionMapper subscriptionMapper;

    /** 通知服务 */
    private final NotificationService notificationService;

    /** Redis 操作模板 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 租户到期扫描的 Redis 锁键 */
    private static final String TENANT_EXPIRY_LOCK_KEY = "scheduler:tenant-expiry-lock";

    /** 订阅到期扫描的 Redis 锁键 */
    private static final String SUBSCRIPTION_EXPIRY_LOCK_KEY = "scheduler:subscription-expiry-lock";

    /** 分布式锁过期时间（秒） */
    private static final long LOCK_EXPIRE_SECONDS = 3600;

    /** 到期提醒天数阈值 */
    private static final List<Long> WARNING_DAYS = Arrays.asList(7L, 3L, 1L);

    /**
     * 扫描即将到期和已到期的租户。
     * <p>
     * 每天凌晨 2 点执行，检查所有活跃和试用中的租户：
     * <ul>
     *   <li>距到期 7、3、1 天时发送到期预警通知</li>
     *   <li>已到期时将租户状态更新为 EXPIRED 并发送到期通知</li>
     * </ul>
     * </p>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scanExpiringTenants() {
        log.info("定时任务[租户到期扫描]开始执行");

        // 尝试获取 Redis 分布式锁，防止多实例重复执行
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(TENANT_EXPIRY_LOCK_KEY, String.valueOf(System.currentTimeMillis()),
                        LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("定时任务[租户到期扫描]未获取到分布式锁，跳过本次执行");
            return;
        }

        int processedCount = 0;
        int expiredCount = 0;
        int notificationCount = 0;

        try {
            // 忽略租户过滤，扫描所有租户
            TenantContext.setIgnoreTenant(true);

            // 查询所有活跃或试用中且设置了到期时间的租户
            List<Tenant> tenants = tenantMapper.selectList(
                    new LambdaQueryWrapper<Tenant>()
                            .in(Tenant::getStatus, TenantStatus.ACTIVE, TenantStatus.TRIAL)
                            .isNotNull(Tenant::getExpiredAt)
            );

            LocalDateTime now = LocalDateTime.now();

            for (Tenant tenant : tenants) {
                processedCount++;
                long daysUntilExpiry = ChronoUnit.DAYS.between(now, tenant.getExpiredAt());

                if (daysUntilExpiry <= 0) {
                    // 租户已到期，更新状态为 EXPIRED
                    tenant.setStatus(TenantStatus.EXPIRED);
                    tenantMapper.updateById(tenant);
                    expiredCount++;

                    // 发送到期通知
                    notificationService.sendToTenant(
                            tenant.getId(),
                            "租户到期提醒",
                            "您的租户已到期，请联系管理员续费",
                            NotificationType.EXPIRY_WARNING
                    );
                    notificationCount++;

                    log.info("租户已到期并更新状态：租户ID={}, 租户名称={}, 到期时间={}",
                            tenant.getId(), tenant.getName(), tenant.getExpiredAt());
                } else if (WARNING_DAYS.contains(daysUntilExpiry)) {
                    // 租户即将到期且在提醒阈值内，发送预警通知
                    notificationService.sendToTenant(
                            tenant.getId(),
                            "租户到期提醒",
                            String.format("您的租户将于 %d 天后到期，请及时续费", daysUntilExpiry),
                            NotificationType.EXPIRY_WARNING
                    );
                    notificationCount++;

                    log.info("租户到期预警已发送：租户ID={}, 租户名称={}, 剩余天数={}",
                            tenant.getId(), tenant.getName(), daysUntilExpiry);
                }
            }
        } catch (Exception e) {
            log.error("定时任务[租户到期扫描]执行异常", e);
        } finally {
            // 清理租户上下文，防止内存泄漏
            TenantContext.clear();
            // 释放分布式锁
            stringRedisTemplate.delete(TENANT_EXPIRY_LOCK_KEY);
        }

        log.info("定时任务[租户到期扫描]执行完成：处理租户数={}, 到期数={}, 发送通知数={}",
                processedCount, expiredCount, notificationCount);
    }

    /**
     * 扫描已到期的订阅。
     * <p>
     * 每天凌晨 3 点执行，将所有状态为 ACTIVE 且到期日期早于今天的订阅标记为 EXPIRED。
     * </p>
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scanExpiredSubscriptions() {
        log.info("定时任务[订阅到期扫描]开始执行");

        // 尝试获取 Redis 分布式锁，防止多实例重复执行
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(SUBSCRIPTION_EXPIRY_LOCK_KEY, String.valueOf(System.currentTimeMillis()),
                        LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("定时任务[订阅到期扫描]未获取到分布式锁，跳过本次执行");
            return;
        }

        try {
            // 忽略租户过滤，扫描所有订阅
            TenantContext.setIgnoreTenant(true);

            LocalDate today = LocalDate.now();

            // 查询所有状态为 ACTIVE 且到期日期早于今天的订阅
            List<Subscription> expiredSubscriptions = subscriptionMapper.selectList(
                    new LambdaQueryWrapper<Subscription>()
                            .eq(Subscription::getStatus, SubscriptionStatus.ACTIVE)
                            .lt(Subscription::getEndDate, today)
            );

            if (!expiredSubscriptions.isEmpty()) {
                // 批量更新订阅状态为 EXPIRED
                for (Subscription subscription : expiredSubscriptions) {
                    subscription.setStatus(SubscriptionStatus.EXPIRED);
                    subscriptionMapper.updateById(subscription);
                }

                log.info("定时任务[订阅到期扫描]已将 {} 条到期订阅状态更新为 EXPIRED", expiredSubscriptions.size());
            } else {
                log.info("定时任务[订阅到期扫描]未发现已到期的订阅");
            }
        } catch (Exception e) {
            log.error("定时任务[订阅到期扫描]执行异常", e);
        } finally {
            // 清理租户上下文，防止内存泄漏
            TenantContext.clear();
            // 释放分布式锁
            stringRedisTemplate.delete(SUBSCRIPTION_EXPIRY_LOCK_KEY);
        }

        log.info("定时任务[订阅到期扫描]执行完成");
    }
}
