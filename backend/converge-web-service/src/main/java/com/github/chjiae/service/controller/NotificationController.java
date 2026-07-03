package com.github.chjiae.service.controller;

import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.dto.notification.NotificationResponse;
import com.github.chjiae.service.dto.notification.SendNotificationRequest;
import com.github.chjiae.service.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 站内信控制器，提供通知的查询、标记已读和发布公告接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    /** 站内信服务 */
    private final NotificationService notificationService;

    /**
     * 获取当前用户的通知列表
     *
     * @param page 页码，默认 1
     * @param size 每页数量，默认 10
     * @return 分页通知列表
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<NotificationResponse>> listNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询通知列表接口调用，页码: {}，每页数量: {}", page, size);
        PageResult<NotificationResponse> response = notificationService.listNotifications(page, size);
        return Result.ok(response);
    }

    /**
     * 标记通知已读
     *
     * @param id 通知 ID
     * @return 成功响应
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> markAsRead(@PathVariable Long id) {
        log.info("标记通知已读接口调用，通知 ID: {}", id);
        notificationService.markAsRead(id);
        return Result.ok();
    }

    /**
     * 全部标记已读
     *
     * @return 成功响应
     */
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> markAllAsRead() {
        log.info("全部标记已读接口调用");
        notificationService.markAllAsRead();
        return Result.ok();
    }

    /**
     * 获取未读通知数量
     *
     * @return 未读通知数量
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> getUnreadCount() {
        log.info("查询未读通知数量接口调用");
        Long count = notificationService.getUnreadCount();
        return Result.ok(count);
    }

    /**
     * 发送平台公告（超管使用）
     *
     * @param request 发送公告请求参数
     * @return 成功响应
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> sendAnnouncement(@Valid @RequestBody SendNotificationRequest request) {
        log.info("发送平台公告接口调用，标题: {}，目标租户: {}", request.getTitle(),
                request.getTenantId() != null ? request.getTenantId() : "全部");
        notificationService.sendPlatformAnnouncement(
                request.getTitle(), request.getContent(), request.getTenantId());
        return Result.ok();
    }
}
