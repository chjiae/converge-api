package com.github.chjiae.service.dto.subscription;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅响应 DTO，返回给前端的订阅信息。
 */
@Data
@Builder
public class SubscriptionResponse {

    /** 订阅 ID */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 套餐类型 */
    private String planType;

    /** 金额 */
    private BigDecimal amount;

    /** 生效日期 */
    private LocalDate startDate;

    /** 到期日期 */
    private LocalDate endDate;

    /** 订阅状态 */
    private String status;

    /** 支付方式 */
    private String paymentMethod;

    /** 支付凭证号 */
    private String paymentRef;

    /** 备注 */
    private String remark;

    /** 创建人 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
