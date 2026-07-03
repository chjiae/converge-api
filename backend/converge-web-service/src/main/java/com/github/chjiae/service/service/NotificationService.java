package com.github.chjiae.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.enums.NotificationType;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.notification.NotificationResponse;
import com.github.chjiae.service.entity.Notification;
import com.github.chjiae.service.mapper.NotificationMapper;
import com.github.chjiae.service.security.UserPrincipal;
import com.github.chjiae.service.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内信服务，管理系统通知和消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 通知数据访问层 */
    private final NotificationMapper notificationMapper;

    /**
     * 发送通知给指定用户
     *
     * @param tenantId 租户 ID
     * @param userId   接收人 ID
     * @param title    通知标题
     * @param content  通知内容
     * @param type     通知类型
     */
    public void send(Long tenantId, Long userId, String title, String content, NotificationType type) {
        Notification notification = new Notification();
        notification.setTenantId(tenantId);
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        // 发送通知时需绕过租户过滤
        TenantContext.setIgnoreTenant(true);
        notificationMapper.insert(notification);
        log.info("站内信已发送：租户={}, 用户={}, 标题={}, 类型={}", tenantId, userId, title, type);
    }

    /**
     * 发送通知给租户下所有用户
     * userId 设为 null 表示该租户下所有用户均可收到
     *
     * @param tenantId 租户 ID
     * @param title    通知标题
     * @param content  通知内容
     * @param type     通知类型
     */
    public void sendToTenant(Long tenantId, String title, String content, NotificationType type) {
        send(tenantId, null, title, content, type);
    }

    /**
     * 发送平台公告（给所有租户或指定租户）
     * tenantId 为 null 表示所有租户均可见
     *
     * @param title    公告标题
     * @param content  公告内容
     * @param tenantId 目标租户 ID（null 表示所有租户）
     */
    public void sendPlatformAnnouncement(String title, String content, Long tenantId) {
        send(tenantId, null, title, content, NotificationType.SYSTEM);
        log.info("平台公告已发布：标题={}, 目标租户={}", title, tenantId != null ? tenantId : "全部");
    }

    /**
     * 获取当前用户的通知列表
     * notification 表不在 IGNORE_TABLES 中，需手动忽略租户过滤并添加自定义条件
     *
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @return 分页通知列表
     */
    public PageResult<NotificationResponse> listNotifications(int page, int size) {
        UserPrincipal principal = requireCurrentUser();
        Long currentTenantId = principal.getTenantId();
        Long currentUserId = principal.getUserId();

        log.info("查询通知列表，用户 ID: {}，租户 ID: {}，页码: {}，每页数量: {}", currentUserId, currentTenantId, page, size);

        // 手动忽略租户过滤，添加自定义查询条件
        TenantContext.setIgnoreTenant(true);

        Page<Notification> pageParam = new Page<>(page, size);
        Page<Notification> resultPage = notificationMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Notification>()
                        // 租户匹配：通知的 tenantId 等于当前租户，或者 tenantId 为 null（全平台公告）
                        .and(w -> {
                            if (currentTenantId != null) {
                                w.eq(Notification::getTenantId, currentTenantId).or().isNull(Notification::getTenantId);
                            } else {
                                // 超管：只看全平台公告（tenantId 为 null）
                                w.isNull(Notification::getTenantId);
                            }
                        })
                        // 用户匹配：通知的 userId 等于当前用户，或者 userId 为 null（全员通知）
                        .and(w -> w.eq(Notification::getUserId, currentUserId).or().isNull(Notification::getUserId))
                        .orderByDesc(Notification::getCreatedAt)
        );

        List<NotificationResponse> list = resultPage.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, resultPage.getTotal(), page, size);
    }

    /**
     * 标记通知已读
     * 仅允许标记当前用户自己的通知
     *
     * @param notificationId 通知 ID
     */
    public void markAsRead(Long notificationId) {
        UserPrincipal principal = requireCurrentUser();
        Long currentUserId = principal.getUserId();

        log.info("标记通知已读，通知 ID: {}，用户 ID: {}", notificationId, currentUserId);

        // 忽略租户过滤，手动校验归属
        TenantContext.setIgnoreTenant(true);

        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(404, "通知不存在");
        }

        // 校验通知是否属于当前用户（userId 为 null 的全员通知或 userId 匹配）
        if (notification.getUserId() != null && !notification.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "无权操作该通知");
        }

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(notification);
        }
    }

    /**
     * 全部标记已读
     * 将当前用户的所有未读通知标记为已读
     */
    public void markAllAsRead() {
        UserPrincipal principal = requireCurrentUser();
        Long currentUserId = principal.getUserId();
        Long currentTenantId = principal.getTenantId();

        log.info("全部标记已读，用户 ID: {}，租户 ID: {}", currentUserId, currentTenantId);

        // 忽略租户过滤，手动添加条件
        TenantContext.setIgnoreTenant(true);

        // 使用 update 批量标记已读
        LambdaUpdateWrapper<Notification> updateWrapper = new LambdaUpdateWrapper<Notification>()
                .set(Notification::getIsRead, true)
                .set(Notification::getReadAt, LocalDateTime.now())
                .eq(Notification::getIsRead, false)
                // 用户匹配：userId 等于当前用户或 userId 为 null
                .and(w -> w.eq(Notification::getUserId, currentUserId).or().isNull(Notification::getUserId))
                // 租户匹配
                .and(w -> {
                    if (currentTenantId != null) {
                        w.eq(Notification::getTenantId, currentTenantId).or().isNull(Notification::getTenantId);
                    } else {
                        w.isNull(Notification::getTenantId);
                    }
                });

        notificationMapper.update(null, updateWrapper);
    }

    /**
     * 获取未读通知数量
     *
     * @return 未读通知数量
     */
    public Long getUnreadCount() {
        UserPrincipal principal = requireCurrentUser();
        Long currentUserId = principal.getUserId();
        Long currentTenantId = principal.getTenantId();

        // 忽略租户过滤，手动添加条件
        TenantContext.setIgnoreTenant(true);

        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getIsRead, false)
                        .and(w -> w.eq(Notification::getUserId, currentUserId).or().isNull(Notification::getUserId))
                        .and(w -> {
                            if (currentTenantId != null) {
                                w.eq(Notification::getTenantId, currentTenantId).or().isNull(Notification::getTenantId);
                            } else {
                                w.isNull(Notification::getTenantId);
                            }
                        })
        );
    }

    /**
     * 获取当前登录用户主体（必须已认证）
     *
     * @return 当前用户主体
     * @throws BusinessException 未认证时抛出
     */
    private UserPrincipal requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        throw new BusinessException(401, "用户未认证");
    }

    /**
     * 将通知实体转换为响应 DTO
     *
     * @param entity 通知实体
     * @return 通知响应 DTO
     */
    private NotificationResponse toResponse(Notification entity) {
        return NotificationResponse.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .type(entity.getType() != null ? entity.getType().name() : null)
                .isRead(entity.getIsRead())
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
