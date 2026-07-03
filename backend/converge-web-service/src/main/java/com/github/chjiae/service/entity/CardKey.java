package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.CardKeyStatus;
import com.github.chjiae.common.enums.SubscriptionPlanType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 卡密实体，对应 card_key 表。
 * 记录卡密的编码、套餐类型、面值、状态及兑换信息。
 * 不继承 BaseEntity，因为 card_key 表没有 updated_at 列。
 */
@Data
@TableName("card_key")
public class CardKey implements Serializable {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 卡密编码（唯一，16 位大写字母+数字） */
    private String code;

    /** 套餐类型：TRIAL / MONTHLY / QUARTERLY / YEARLY / CUSTOM */
    private SubscriptionPlanType planType;

    /** 有效天数 */
    private Integer durationDays;

    /** 面值金额 */
    private BigDecimal amount;

    /** 卡密状态：UNUSED / REDEEMED / EXPIRED */
    private CardKeyStatus status;

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
