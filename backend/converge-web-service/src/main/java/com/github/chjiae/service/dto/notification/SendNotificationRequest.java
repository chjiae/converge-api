package com.github.chjiae.service.dto.notification;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送平台公告请求 DTO（超管使用）。
 */
@Data
public class SendNotificationRequest {

    /** 通知标题 */
    @NotBlank(message = "通知标题不能为空")
    private String title;

    /** 通知内容 */
    @NotBlank(message = "通知内容不能为空")
    private String content;

    /** 目标租户 ID（null 表示所有租户） */
    private Long tenantId;
}
