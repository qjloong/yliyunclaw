package vip.mate.auth.yliyun.runtime;

/** Header names used by the Yliyun internal runtime contract. */
public final class YliyunRuntimeHeaders {

    public static final String INTERNAL_TOKEN = "X-Yly-Internal-Token";
    public static final String TENANT_ID = "X-Yly-Tenant-Id";
    public static final String USER_ID = "X-Yly-User-Id";
    public static final String WORKSPACE_ID = "X-Yly-Workspace-Id";
    public static final String APP_CODE = "X-Yly-App-Code";
    public static final String BIZ_TYPE = "X-Yly-Biz-Type";
    public static final String BIZ_ID = "X-Yly-Biz-Id";
    public static final String RUN_ID = "X-Yly-Run-Id";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String TIMESTAMP = "X-Yly-Timestamp";
    public static final String NONCE = "X-Yly-Nonce";
    public static final String SIGNATURE = "X-Yly-Signature";
    public static final String TRUSTED_CONTEXT = "X-Yly-Trusted-Context";
    public static final String CONTEXT_SHA256 = "X-Yly-Context-SHA256";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private YliyunRuntimeHeaders() {
    }
}
