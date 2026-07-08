package com.github.chjiae.gateway.governance;

import com.github.chjiae.contract.gateway.GatewayExecutionResourceRuntimePolicySnapshot;
import com.github.chjiae.gateway.snapshot.GatewayOpenAiExecutionTarget;

/**
 * 网关运行时 lease。
 * lease ID 只用于 Redis 状态校验，禁止输出到日志、错误响应或状态接口。
 */
public final class GatewayRuntimeLease {

    /** 执行目标 */
    private final GatewayOpenAiExecutionTarget target;

    /** 运行时策略 */
    private final GatewayExecutionResourceRuntimePolicySnapshot policy;

    /** lease ID */
    private final String leaseId;

    /** 是否半开探测 */
    private final boolean halfOpen;

    /**
     * 创建运行时 lease。
     *
     * @param target 执行目标
     * @param policy 运行时策略
     * @param leaseId lease ID
     * @param halfOpen 是否半开探测
     */
    public GatewayRuntimeLease(GatewayOpenAiExecutionTarget target,
                               GatewayExecutionResourceRuntimePolicySnapshot policy,
                               String leaseId,
                               boolean halfOpen) {
        this.target = target;
        this.policy = policy;
        this.leaseId = leaseId;
        this.halfOpen = halfOpen;
    }

    /**
     * 获取执行目标。
     *
     * @return 执行目标
     */
    public GatewayOpenAiExecutionTarget target() {
        return target;
    }

    /**
     * 获取运行时策略。
     *
     * @return 运行时策略
     */
    public GatewayExecutionResourceRuntimePolicySnapshot policy() {
        return policy;
    }

    /**
     * 获取 lease ID。
     *
     * @return lease ID
     */
    public String leaseId() {
        return leaseId;
    }

    /**
     * 判断是否半开探测。
     *
     * @return true 表示半开探测
     */
    public boolean halfOpen() {
        return halfOpen;
    }
}
