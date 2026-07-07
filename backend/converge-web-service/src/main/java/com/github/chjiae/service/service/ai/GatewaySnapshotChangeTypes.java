package com.github.chjiae.service.service.ai;

/**
 * AI 网关快照变更类型常量。
 */
public final class GatewaySnapshotChangeTypes {

    /** Provider 变更。 */
    public static final String AI_PROVIDER_CHANGED = "AI_PROVIDER_CHANGED";

    /** Connection 变更。 */
    public static final String AI_CONNECTION_CHANGED = "AI_CONNECTION_CHANGED";

    /** PublicModel 变更。 */
    public static final String AI_PUBLIC_MODEL_CHANGED = "AI_PUBLIC_MODEL_CHANGED";

    /** Credential 变更。 */
    public static final String AI_CREDENTIAL_CHANGED = "AI_CREDENTIAL_CHANGED";

    /** ExecutionResource 变更。 */
    public static final String AI_EXECUTION_RESOURCE_CHANGED = "AI_EXECUTION_RESOURCE_CHANGED";

    /** ResourcePool 变更。 */
    public static final String AI_RESOURCE_POOL_CHANGED = "AI_RESOURCE_POOL_CHANGED";

    /** ResourcePoolMember 变更。 */
    public static final String AI_RESOURCE_POOL_MEMBER_CHANGED = "AI_RESOURCE_POOL_MEMBER_CHANGED";

    /** ResourceModelBinding 变更。 */
    public static final String AI_RESOURCE_MODEL_BINDING_CHANGED = "AI_RESOURCE_MODEL_BINDING_CHANGED";

    /** RoutePolicy 变更。 */
    public static final String AI_ROUTE_POLICY_CHANGED = "AI_ROUTE_POLICY_CHANGED";

    /** RouteTarget 变更。 */
    public static final String AI_ROUTE_TARGET_CHANGED = "AI_ROUTE_TARGET_CHANGED";

    private GatewaySnapshotChangeTypes() {
    }
}
