package com.github.chjiae.service.dto.notification;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内信响应 DTO，返回给前端的通知信息。
 */
@Data
@Builder
public class NotificationResponse {

    /** 通知 ID */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 接收人 ID */
    private Long userId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型 */
    private String type;

    /** 是否已读 */
    private Boolean isRead;

    /** 阅读时间 */
    private LocalDateTime readAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
