package com.github.chjiae.routing;

import java.util.List;

/**
 * 静态路由预览选择结果。
 *
 * @param selectedPoolId 按 seed 选中的资源池 ID
 * @param selectedPoolCode 按 seed 选中的资源池编码
 * @param selectedResourceId 按 seed 选中的执行资源 ID
 * @param upstreamModelName 精确上游模型名称
 * @param warnings 固定安全警告
 */
public record StaticRoutePreview(
        String selectedPoolId,
        String selectedPoolCode,
        String selectedResourceId,
        String upstreamModelName,
        List<String> warnings
) {

    /**
     * 复制集合，保持不可变。
     */
    public StaticRoutePreview {
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
