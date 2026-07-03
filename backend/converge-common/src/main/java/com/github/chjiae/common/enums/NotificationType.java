package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知类型枚举，用于标识站内信的消息分类
 */
@Getter
@AllArgsConstructor
public enum NotificationType {

    /** 系统通知，平台公告等系统级消息 */
    SYSTEM("系统通知"),
    /** 审核结果通知，申请审核通过或拒绝时发送 */
    AUDIT_RESULT("审核结果"),
    /** 到期提醒通知，订阅即将到期时发送 */
    EXPIRY_WARNING("到期提醒"),
    /** 订阅通知，订阅创建、支付成功等相关消息 */
    SUBSCRIPTION("订阅通知");

    /** 通知类型描述 */
    private final String description;
}
