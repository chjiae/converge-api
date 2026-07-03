package com.github.chjiae.service.dto.cardkey;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 生成卡密请求参数。
 * 超管批量生成卡密时使用。
 */
@Data
public class GenerateCardKeyRequest {

    /** 套餐类型（必填，如 MONTHLY / YEARLY 等） */
    @NotBlank(message = "套餐类型不能为空")
    private String planType;

    /** 有效天数（必填） */
    @NotNull(message = "有效天数不能为空")
    @Min(value = 1, message = "有效天数必须大于 0")
    private Integer durationDays;

    /** 面值金额（必填） */
    @NotNull(message = "面值金额不能为空")
    @Min(value = 0, message = "面值金额不能为负数")
    private BigDecimal amount;

    /** 生成数量（必填，最大 100） */
    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量最少为 1")
    @Max(value = 100, message = "单次最多生成 100 张卡密")
    private Integer count;
}
