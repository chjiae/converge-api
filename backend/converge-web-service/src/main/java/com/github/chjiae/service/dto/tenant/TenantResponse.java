package com.github.chjiae.service.dto.tenant;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户响应 DTO，返回给前端的租户信息。
 */
@Data
@Builder
public class TenantResponse {

    /** 租户 ID */
    private Long id;

    /** 租户编码 */
    private String code;

    /** 租户名称 */
    private String name;

    /** 租户描述 */
    private String description;

    /** 租户状态 */
    private String status;

    /** 是否已使用过试用 */
    private Boolean trialUsed;

    /** 到期时间（null 表示永不过期） */
    private LocalDateTime expiredAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 创建人 ID */
    private Long createdBy;
}
