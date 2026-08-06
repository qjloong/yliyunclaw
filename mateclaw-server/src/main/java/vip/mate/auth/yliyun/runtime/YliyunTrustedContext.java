package vip.mate.auth.yliyun.runtime;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Frozen trusted context consumed by MateClaw internal runtime endpoints. */
public record YliyunTrustedContext(
        long tenantId,
        long userId,
        String workspaceId,
        String appCode,
        String bizType,
        String bizId,
        String conversationId,
        String runId,
        String traceId,
        List<String> roles,
        List<String> capabilities,
        long sourceVersion,
        long issuedAt,
        long expiresAt
) {

    private static final Set<String> APP_CODES = Set.of("GOAL", "AI_SITES");
    private static final Set<String> ROLES = Set.of("OWNER", "MANAGER", "MEMBER", "VIEWER");
    private static final Set<String> CAPABILITIES = Set.of(
            "PROJECT_READ", "TASK_DRAFT", "TASK_SUBMIT", "TASK_ACCEPT");
    private static final Pattern WORKSPACE = Pattern.compile("ws_[A-Za-z0-9_-]{1,128}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");

    public YliyunTrustedContext {
        roles = roles == null ? Collections.emptyList() : List.copyOf(roles);
        capabilities = capabilities == null ? Collections.emptyList() : List.copyOf(capabilities);
    }

    public void validate(Instant now) {
        long current = now.getEpochSecond();
        if (tenantId <= 0 || userId <= 0) {
            throw contextMissing("tenantId and userId must be positive");
        }
        if (workspaceId == null || !WORKSPACE.matcher(workspaceId).matches()) {
            throw contextMissing("workspaceId must use the ws_* format");
        }
        if (!APP_CODES.contains(appCode)) {
            throw contextMissing("appCode is not supported");
        }
        if (!validIdentifier(bizType) || !validIdentifier(bizId)) {
            throw contextMissing("bizType and bizId are required");
        }
        if (conversationId != null && !validIdentifier(conversationId)) {
            throw contextMissing("conversationId is invalid");
        }
        if (runId != null && !validIdentifier(runId)) {
            throw contextMissing("runId is invalid");
        }
        if (!isUuidV4(traceId)) {
            throw contextMissing("traceId must be a UUID v4 value");
        }
        if (!ROLES.containsAll(roles)) {
            throw contextMissing("roles contains an unsupported value");
        }
        if (!CAPABILITIES.containsAll(capabilities)) {
            throw contextMissing("capabilities contains an unsupported value");
        }
        if (sourceVersion < 0) {
            throw new YliyunRuntimeAuthException(
                    "CONTEXT_VERSION_MISMATCH", 401,
                    "Trusted Context sourceVersion is invalid",
                    "auth.context_validation",
                    "Regenerate Trusted Context from the cloud backend", false);
        }
        if (issuedAt > current + 10 || expiresAt <= issuedAt
                || expiresAt - issuedAt > 300 || expiresAt < current) {
            throw new YliyunRuntimeAuthException(
                    "CONTEXT_EXPIRED", 401,
                    "Trusted Context is expired or has an invalid lifetime",
                    "auth.context_validation",
                    "Regenerate Trusted Context from the cloud backend", false);
        }
    }

    private static boolean validIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    private static boolean isUuidV4(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.version() == 4 && value.equalsIgnoreCase(uuid.toString());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static YliyunRuntimeAuthException contextMissing(String message) {
        return new YliyunRuntimeAuthException(
                "CONTEXT_MISSING", 401, message,
                "auth.context_validation",
                "Regenerate Trusted Context from the cloud backend", false);
    }
}
