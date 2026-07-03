package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.NotificationType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内信实体，对应 notification 表。
 * 记录发送给用户的站内消息，包含消息类型、内容和已读状态。
 * 该表有 tenant_id 但没有 updated_at 字段，因此不继承 BaseEntity，自定义所需字段。
 */
@Data
@TableName("notification")
public class Notification implements Serializable {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID（平台级通知为 null） */
    private Long tenantId;

    /** 接收人 ID（null 表示租户下所有用户） */
    private Long userId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型：SYSTEM / AUDIT_RESULT / EXPIRY_WARNING / SUBSCRIPTION */
    private NotificationType type;

    /** 是否已读（false-未读，true-已读） */
    private Boolean isRead;

    /** 阅读时间 */
    private LocalDateTime readAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
