package vip.mate.auth.yliyun;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.exception.MateClawException;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Issues and resolves tenant/user-bound cloud resource references.
 *
 * <p>The browser only carries an opaque {@code refId}; tenant, user and
 * entitlement identity are signed server-side. Resolution always compares
 * those claims with the current authenticated Yliyun mapping before MCP/OBO
 * reads run, so changing an id or replaying another tenant's ref fails closed.</p>
 */
@Service
@RequiredArgsConstructor
public class YliyunCloudResourceRefService {

    public static final String PATH_PREFIX = "yliyun-ref://";
    private static final String SIGNING_DOMAIN = "CLOUD_RESOURCE_REF_V1\n";
    private static final String AUDIENCE = "mateclaw-cloud-resource";
    private static final Pattern PATH_PATTERN = Pattern.compile(
            "^yliyun-ref://([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)$");
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9]\\d{0,18}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Set<String> RESOURCE_TYPES = Set.of("file", "folder");
    private static final Set<String> BINDINGS = Set.of("current-preview", "mention", "pinned");
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private final YliyunTicketKeyRing keyRing;
    private final YliyunUserMappingService userMappingService;
    private final ObjectMapper objectMapper;

    @Value("${mateclaw.auth.yliyun.app-key:mateclaw_ai_assistant}")
    private String appKey;

    @Value("${mateclaw.auth.yliyun.resource-ref-ttl-seconds:604800}")
    private long ttlSeconds;

    public IssuedResourceRef issue(IssueRequest request, Long mateUserId) {
        if (request == null) {
            throw invalidRef("云盘资源引用请求不能为空");
        }
        McWorkspaceUserEntity mapping = requireMapping(mateUserId);
        String type = normalizeType(request.resourceType());
        String resourceId = normalizeResourceId(request.resourceId());
        String binding = normalizeBinding(request.binding());
        String displayName = normalizeOptional(request.displayName(), 255, "displayName");
        String versionId = normalizeVersion(request.versionId());
        String mimeType = normalizeOptional(request.mimeType(), 128, "mimeType");
        long now = Instant.now().getEpochSecond();
        long effectiveTtl = Math.max(60, Math.min(ttlSeconds, 30L * 24 * 60 * 60));

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("v", 1);
        claims.put("aud", AUDIENCE);
        claims.put("appKey", appKey);
        claims.put("kid", keyRing.currentKeyId());
        claims.put("tenantId", mapping.getYliyunTenantId());
        claims.put("userId", mapping.getYliyunUserId());
        claims.put("workspaceId", String.valueOf(mapping.getWorkspaceId()));
        claims.put("configVersion", mapping.getConfigVersion());
        claims.put("resourceType", type);
        claims.put("resourceId", resourceId);
        claims.put("binding", binding);
        if (displayName != null) claims.put("displayName", displayName);
        if (versionId != null) claims.put("versionId", versionId);
        if (mimeType != null) claims.put("mimeType", mimeType);
        claims.put("iat", now);
        claims.put("exp", now + effectiveTtl);

        try {
            String payload = objectMapper.writeValueAsString(claims);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    payload.getBytes(StandardCharsets.UTF_8));
            String refId = encoded + "." + keyRing.signBase64Url(SIGNING_DOMAIN + payload);
            return new IssuedResourceRef(
                    refId,
                    PATH_PREFIX + refId,
                    type,
                    resourceId,
                    displayName,
                    versionId,
                    binding,
                    mimeType,
                    now + effectiveTtl);
        } catch (MateClawException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MateClawException("err.auth.yliyun.resource_ref_issue", 500,
                    "云盘资源引用签发失败");
        }
    }

    /**
     * Rebind an already verified reference without sending the raw cloud id
     * back through the browser. This is used when a current-preview reference
     * becomes conversation-pinned (or is unpinned again).
     */
    public IssuedResourceRef rebind(RebindRequest request, Long mateUserId) {
        if (request == null || request.refId() == null || request.refId().isBlank()) {
            throw invalidRef("refId 格式无效");
        }
        String path = request.refId().startsWith(PATH_PREFIX)
                ? request.refId() : PATH_PREFIX + request.refId();
        ResolvedResourceRef resolved = resolvePath(
                path,
                ChatOrigin.web(null, null, null, null, null, mateUserId));
        return issue(new IssueRequest(
                resolved.resourceType(),
                resolved.resourceId(),
                resolved.displayName(),
                resolved.versionId(),
                normalizeBinding(request.binding()),
                resolved.mimeType()), mateUserId);
    }

    public ResolvedResourceRef resolvePath(String path, ChatOrigin origin) {
        Matcher matcher = PATH_PATTERN.matcher(path == null ? "" : path);
        if (!matcher.matches()) {
            throw invalidRef("云盘资源引用格式无效");
        }
        String[] tokenParts = matcher.group(1).split("\\.", 2);
        try {
            String payload = new String(Base64.getUrlDecoder().decode(tokenParts[0]),
                    StandardCharsets.UTF_8);
            Map<String, Object> claims = objectMapper.readValue(payload,
                    new TypeReference<Map<String, Object>>() { });
            String keyId = stringClaim(claims, "kid");
            String verifiedKeyId = keyRing.verifyBase64Url(
                    SIGNING_DOMAIN + payload, tokenParts[1], keyId);
            if (!Objects.equals(keyId, verifiedKeyId)) {
                throw invalidRef("云盘资源引用签名无效");
            }
            validateProtocolClaims(claims);
            McWorkspaceUserEntity mapping = requireMapping(
                    origin != null ? origin.requesterUserId() : null);
            validateIdentityClaims(claims, mapping, origin);
            return new ResolvedResourceRef(
                    normalizeType(stringClaim(claims, "resourceType")),
                    normalizeResourceId(stringClaim(claims, "resourceId")),
                    optionalStringClaim(claims, "displayName"),
                    optionalStringClaim(claims, "versionId"),
                    normalizeBinding(stringClaim(claims, "binding")),
                    optionalStringClaim(claims, "mimeType"));
        } catch (MateClawException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidRef("云盘资源引用无法解析");
        }
    }

    public boolean supports(String path) {
        // Treat every value in this namespace as ours. Malformed signed refs
        // must enter resolvePath and fail closed instead of being ignored as an
        // ordinary/local attachment.
        return path != null && path.startsWith(PATH_PREFIX);
    }

    private void validateProtocolClaims(Map<String, Object> claims) {
        if (numberClaim(claims, "v") != 1
                || !AUDIENCE.equals(stringClaim(claims, "aud"))
                || !appKey.equals(stringClaim(claims, "appKey"))) {
            throw invalidRef("云盘资源引用协议无效");
        }
        long now = Instant.now().getEpochSecond();
        long issuedAt = numberClaim(claims, "iat");
        long expiresAt = numberClaim(claims, "exp");
        if (issuedAt > now + 60 || expiresAt < now || expiresAt <= issuedAt
                || expiresAt - issuedAt > 30L * 24 * 60 * 60) {
            throw invalidRef("云盘资源引用已过期或时间无效");
        }
    }

    private void validateIdentityClaims(
            Map<String, Object> claims,
            McWorkspaceUserEntity mapping,
            ChatOrigin origin) {
        boolean matches = Objects.equals(stringClaim(claims, "tenantId"), mapping.getYliyunTenantId())
                && Objects.equals(stringClaim(claims, "userId"), mapping.getYliyunUserId())
                && Objects.equals(stringClaim(claims, "workspaceId"), String.valueOf(mapping.getWorkspaceId()))
                && Objects.equals(stringClaim(claims, "appKey"), mapping.getAppKey())
                && Objects.equals((int) numberClaim(claims, "configVersion"), mapping.getConfigVersion());
        if (origin != null && origin.workspaceId() != null) {
            matches = matches && Objects.equals(origin.workspaceId(), mapping.getWorkspaceId());
        }
        if (!matches) {
            throw invalidRef("云盘资源引用不属于当前租户、用户或工作空间");
        }
    }

    private McWorkspaceUserEntity requireMapping(Long mateUserId) {
        McWorkspaceUserEntity mapping = userMappingService.findMappingByMateUserId(mateUserId)
                .orElseThrow(() -> invalidRef("当前登录用户没有有效的云盘身份映射"));
        if (!Objects.equals(appKey, mapping.getAppKey()) || mapping.getConfigVersion() == null) {
            throw invalidRef("当前云盘应用授权已失效，请重新打开 AI 助手");
        }
        return mapping;
    }

    private String normalizeType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!RESOURCE_TYPES.contains(normalized)) throw invalidRef("resourceType 仅支持 file/folder");
        return normalized;
    }

    private String normalizeResourceId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!POSITIVE_ID.matcher(normalized).matches()
                || new BigInteger(normalized).compareTo(MAX_LONG) > 0) {
            throw invalidRef("resourceId 格式无效");
        }
        return normalized;
    }

    private String normalizeBinding(String value) {
        String normalized = value == null || value.isBlank() ? "mention" : value.trim().toLowerCase();
        if (!BINDINGS.contains(normalized)) {
            throw invalidRef("binding 仅支持 current-preview/mention/pinned");
        }
        return normalized;
    }

    private String normalizeVersion(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!VERSION.matcher(normalized).matches()) throw invalidRef("versionId 格式无效");
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw invalidRef(field + " 格式无效");
        }
        return normalized;
    }

    private String stringClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw invalidRef("缺少 " + key);
        return String.valueOf(value);
    }

    private String optionalStringClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private long numberClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            throw invalidRef("缺少或无法解析 " + key);
        }
    }

    private MateClawException invalidRef(String message) {
        return new MateClawException("err.auth.yliyun.invalid_resource_ref", 403, message);
    }

    public record IssueRequest(
            String resourceType,
            String resourceId,
            String displayName,
            String versionId,
            String binding,
            String mimeType) {
    }

    public record RebindRequest(String refId, String binding) {
    }

    public record IssuedResourceRef(
            String refId,
            String path,
            String resourceType,
            String resourceId,
            String displayName,
            String versionId,
            String binding,
            String mimeType,
            long expiresAt) {
    }

    public record ResolvedResourceRef(
            String resourceType,
            String resourceId,
            String displayName,
            String versionId,
            String binding,
            String mimeType) {
    }
}
