package com.github.chjiae.routing;

import java.util.List;

/**
 * 静态路由编译或校验结果。
 *
 * @param valid 是否通过校验
 * @param errorCategory 安全错误分类
 * @param safeReasons 安全失败原因，不包含秘密
 * @param plan 编译成功的路由计划
 */
public record StaticRouteValidationResult(
        boolean valid,
        String errorCategory,
        List<String> safeReasons,
        StaticRoutePlan plan
) {

    /**
     * 构造成功结果。
     *
     * @param plan 路由计划
     * @return 成功结果
     */
    public static StaticRouteValidationResult valid(StaticRoutePlan plan) {
        return new StaticRouteValidationResult(true, null, List.of(), plan);
    }

    /**
     * 构造失败结果。
     *
     * @param category 错误分类
     * @param reasons 安全失败原因
     * @return 失败结果
     */
    public static StaticRouteValidationResult invalid(String category, List<String> reasons) {
        return new StaticRouteValidationResult(false, category, List.copyOf(reasons), null);
    }

    /**
     * 复制集合，保持不可变。
     */
    public StaticRouteValidationResult {
        safeReasons = List.copyOf(safeReasons == null ? List.of() : safeReasons);
    }
}
