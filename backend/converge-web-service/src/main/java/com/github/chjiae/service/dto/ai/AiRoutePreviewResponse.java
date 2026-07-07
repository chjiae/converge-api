package com.github.chjiae.service.dto.ai;

import com.github.chjiae.routing.StaticRoutePlan;
import com.github.chjiae.routing.StaticRoutePreview;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI 静态路由预览响应 DTO。
 * 不包含 API Key、密文、nonce、Redis key、manifest HMAC 或 payload。
 */
@Data
@Builder
public class AiRoutePreviewResponse {

    /** 校验状态：VALID 或 INVALID */
    private String validationStatus;

    /** 安全错误分类，成功时为空 */
    private String errorCategory;

    /** 安全失败原因列表，成功时为空 */
    private List<String> reasons;

    /** 静态候选层与计划，成功时返回 */
    private StaticRoutePlan routePlan;

    /** 按 seed 得到的确定性预览结果，成功时返回 */
    private StaticRoutePreview preview;

    /** 固定警告，说明预览不代表真实请求执行 */
    private List<String> warnings;
}
