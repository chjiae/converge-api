package com.github.chjiae.service.dto.cardkey;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 卡密响应 DTO，返回给前端的卡密信息。
 */
@Data
@Builder
public class CardKeyResponse {

    /** 卡密 ID */
    private Long id;

    /** 卡密编码 */
    private String code;

    /** 套餐类型 */
    private String planType;

    /** 有效天数 */
    private Integer durationDays;

    /** 面值金额 */
    private BigDecimal amount;

    /** 卡密状态 */
    private String status;

    /** 生成人用户 ID */
    private Long generatedBy;

    /** 兑换人用户 ID */
    private Long redeemedBy;

    /** 兑换时间 */
    private LocalDateTime redeemedAt;

    /** 兑换后所属租户 ID */
    private Long tenantId;

    /** 卡密过期时间 */
    private LocalDateTime expiredAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
