package com.github.chjiae.service.controller;

import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.dto.cardkey.CardKeyResponse;
import com.github.chjiae.service.dto.cardkey.GenerateCardKeyRequest;
import com.github.chjiae.service.dto.cardkey.RedeemCardKeyRequest;
import com.github.chjiae.service.service.CardKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 卡密管理控制器，提供卡密生成、列表查询和兑换接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CardKeyController {

    /** 卡密管理服务 */
    private final CardKeyService cardKeyService;

    /**
     * 批量生成卡密（仅超管可用）
     *
     * @param request 生成卡密请求参数
     * @return 生成的卡密列表
     */
    @PostMapping("/card-keys/generate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<List<CardKeyResponse>> generateCardKeys(@Valid @RequestBody GenerateCardKeyRequest request) {
        log.info("生成卡密接口调用，数量: {}，套餐类型: {}", request.getCount(), request.getPlanType());
        List<CardKeyResponse> response = cardKeyService.generateCardKeys(request);
        return Result.ok(response);
    }

    /**
     * 分页查询卡密列表（仅超管可用）
     *
     * @param page   页码，默认 1
     * @param size   每页数量，默认 10
     * @param status 卡密状态过滤（可选，如 UNUSED / REDEEMED / EXPIRED）
     * @return 分页卡密列表
     */
    @GetMapping("/card-keys")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<PageResult<CardKeyResponse>> listCardKeys(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        log.info("卡密列表接口调用，页码: {}，每页数量: {}，状态: {}", page, size, status);
        PageResult<CardKeyResponse> response = cardKeyService.listCardKeys(page, size, status);
        return Result.ok(response);
    }

    /**
     * 兑换卡密（已认证用户可用）
     * 租户用户使用卡密编码兑换订阅，自动激活并延长有效期。
     *
     * @param request 兑换卡密请求参数
     * @return 兑换后的卡密信息
     */
    @PostMapping("/my-subscriptions/redeem")
    @PreAuthorize("isAuthenticated()")
    public Result<CardKeyResponse> redeemCardKey(@Valid @RequestBody RedeemCardKeyRequest request) {
        log.info("卡密兑换接口调用，编码: {}", request.getCode());
        CardKeyResponse response = cardKeyService.redeemCardKey(request.getCode());
        return Result.ok(response);
    }
}
