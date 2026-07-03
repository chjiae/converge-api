package com.github.chjiae.service.dto.cardkey;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 兑换卡密请求参数。
 * 租户用户使用卡密兑换订阅时使用。
 */
@Data
public class RedeemCardKeyRequest {

    /** 卡密编码（必填） */
    @NotBlank(message = "卡密编码不能为空")
    private String code;
}
