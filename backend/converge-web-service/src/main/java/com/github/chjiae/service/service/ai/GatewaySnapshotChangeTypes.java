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

    /** ExecutionResource 运行时治理策略变更。 */
    public static final String AI_EXECUTION_RESOURCE_RUNTIME_POLICY_CHANGED =
            "AI_EXECUTION_RESOURCE_RUNTIME_POLICY_CHANGED";

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

    /** AccessGroup 变更。 */
    public static final String AI_ACCESS_GROUP_CHANGED = "AI_ACCESS_GROUP_CHANGED";

    /** AccessGroupModelGrant 变更。 */
    public static final String AI_ACCESS_GROUP_MODEL_GRANT_CHANGED = "AI_ACCESS_GROUP_MODEL_GRANT_CHANGED";

    /** ClientApiKey 变更。 */
    public static final String AI_CLIENT_API_KEY_CHANGED = "AI_CLIENT_API_KEY_CHANGED";

    /** ClientApiKeyAccessGroup 变更。 */
    public static final String AI_CLIENT_API_KEY_ACCESS_GROUP_CHANGED = "AI_CLIENT_API_KEY_ACCESS_GROUP_CHANGED";

    private GatewaySnapshotChangeTypes() {
    }
}
