package com.github.chjiae.gateway.snapshot;

/**
 * 网关快照验证异常。
 * 只携带安全错误分类，避免日志或状态接口泄露 Redis key、密文、nonce 或签名。
 */
class GatewaySnapshotValidationException extends RuntimeException {

    /** 安全错误分类 */
    private final String category;

    /**
     * 创建验证异常。
     *
     * @param category 错误分类
     * @param message 错误消息
     */
    GatewaySnapshotValidationException(String category, String message) {
        super(message);
        this.category = category;
    }

    /**
     * 获取安全错误分类。
     *
     * @return 错误分类
     */
    String category() {
        return category;
    }
}
