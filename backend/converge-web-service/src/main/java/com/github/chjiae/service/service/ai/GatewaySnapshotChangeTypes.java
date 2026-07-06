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

    private GatewaySnapshotChangeTypes() {
    }
}
