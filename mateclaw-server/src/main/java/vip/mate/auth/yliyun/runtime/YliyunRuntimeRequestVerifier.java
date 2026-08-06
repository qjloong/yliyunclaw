package vip.mate.auth.yliyun.runtime;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Verifies service identity, HMAC, nonce, trusted context and local bindings. */
@Service
public class YliyunRuntimeRequestVerifier {

    private final ObjectMapper objectMapper;
    private final YliyunRuntimeSecurityProperties properties;
    private final YliyunRuntimeNonceService nonceService;
    private final YliyunTrustedContextCrossValidator crossValidator;

    public YliyunRuntimeRequestVerifier(
            ObjectMapper objectMapper,
            YliyunRuntimeSecurityProperties properties,
            YliyunRuntimeNonceService nonceService,
            YliyunTrustedContextCrossValidator crossValidator) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.nonceService = nonceService;
        this.crossValidator = crossValidator;
    }

    public YliyunTrustedContext verify(CachedBodyHttpServletRequest request) {
        requireConfigured();
        String traceId = requiredHeader(request, YliyunRuntimeHeaders.TRACE_ID);
        verifyServiceTokens(request);

        String timestamp = requiredHeader(request, YliyunRuntimeHeaders.TIMESTAMP);
        String nonce = requiredHeader(request, YliyunRuntimeHeaders.NONCE);
        String suppliedSignature = requiredHeader(request, YliyunRuntimeHeaders.SIGNATURE);
        verifyTimestamp(timestamp);

        String encodedContext = requiredHeader(request, YliyunRuntimeHeaders.TRUSTED_CONTEXT);
        if (encodedContext.length() > properties.getMaxContextBytes() * 2) {
            throw new YliyunRuntimeAuthException(
                    "RATE_LIMITED", 413, "Trusted Context header exceeds the configured limit",
                    "auth.request_limits", "Reduce the Trusted Context size", false);
        }
        byte[] contextBytes = YliyunRuntimeCrypto.decodeBase64Url(encodedContext);
        if (contextBytes.length > properties.getMaxContextBytes()) {
            throw new YliyunRuntimeAuthException(
                    "RATE_LIMITED", 413, "Trusted Context exceeds the configured limit",
                    "auth.request_limits", "Reduce the Trusted Context size", false);
        }
        String actualContextHash = YliyunRuntimeCrypto.sha256Hex(contextBytes);
        String suppliedContextHash = requiredHeader(request, YliyunRuntimeHeaders.CONTEXT_SHA256);
        if (!YliyunRuntimeCrypto.constantTimeUtf8Equals(actualContextHash, suppliedContextHash)) {
            throw authFailure("AUTH_SIGNATURE_MISMATCH",
                    "Trusted Context digest does not match the transmitted context");
        }

        YliyunTrustedContext context = parseContext(contextBytes);
        context.validate(Instant.now());
        verifyHeaderConsistency(request, context, traceId);
        verifyIdempotency(request);

        String bodyHash = YliyunRuntimeCrypto.sha256Hex(request.bodyBytes());
        String canonical;
        byte[] signatureInput;
        if (properties.getSignatureVersion()
                == YliyunRuntimeSecurityProperties.SignatureVersion.CONTEXT_V1_1) {
            canonical = YliyunRuntimeCrypto.canonicalV11(
                    request.getMethod(), request.getRequestURI(), request.getQueryString(),
                    timestamp, nonce, actualContextHash, bodyHash);
            signatureInput = canonical.getBytes(StandardCharsets.UTF_8);
        } else {
            canonical = YliyunRuntimeCrypto.canonicalV1(
                    request.getMethod(), request.getRequestURI(), request.getQueryString(),
                    timestamp, nonce, bodyHash);
            signatureInput = YliyunRuntimeCrypto.legacyMessageBytes(
                    canonical, properties.getLegacyHmacInput());
        }

        String expectedSignature = YliyunRuntimeCrypto.hmacBase64(
                properties.getServiceSecret(), signatureInput);
        if (!YliyunRuntimeCrypto.constantTimeBase64Equals(
                expectedSignature, suppliedSignature)) {
            throw authFailure("AUTH_SIGNATURE_MISMATCH", "Request signature verification failed");
        }

        // Consume only after signature verification to prevent nonce poisoning.
        nonceService.consume(nonce);
        crossValidator.validate(context);
        return context;
    }

    private YliyunTrustedContext parseContext(byte[] contextBytes) {
        try {
            return objectMapper.readValue(contextBytes, YliyunTrustedContext.class);
        } catch (IOException ex) {
            throw new YliyunRuntimeAuthException(
                    "CONTEXT_MISSING", 401, "Trusted Context JSON is invalid",
                    "auth.context_validation",
                    "Regenerate Trusted Context from the cloud backend", false);
        }
    }

    private void verifyServiceTokens(CachedBodyHttpServletRequest request) {
        String authorization = tokenHeader(request, HttpHeaders.AUTHORIZATION);
        String prefix = "Bearer ";
        if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())
                || !YliyunRuntimeCrypto.constantTimeUtf8Equals(
                        properties.getServiceToken(), authorization.substring(prefix.length()))) {
            throw authFailure("AUTH_TOKEN_INVALID", "Service bearer token is invalid");
        }
        if (properties.isRequireInternalToken()) {
            String internal = tokenHeader(request, YliyunRuntimeHeaders.INTERNAL_TOKEN);
            if (!YliyunRuntimeCrypto.constantTimeUtf8Equals(
                    properties.getInternalToken(), internal)) {
                throw authFailure("AUTH_TOKEN_INVALID", "Internal service token is invalid");
            }
        }
    }

    private void verifyTimestamp(String value) {
        long timestamp;
        try {
            timestamp = Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw authFailure("AUTH_TIMESTAMP_EXPIRED", "X-Yly-Timestamp is invalid");
        }
        long now = Instant.now().getEpochSecond();
        if (timestamp < now - properties.getClockSkewSeconds()
                || timestamp > now + properties.getClockSkewSeconds()) {
            throw authFailure("AUTH_TIMESTAMP_EXPIRED", "Request timestamp is outside the allowed window");
        }
    }

    private void verifyHeaderConsistency(CachedBodyHttpServletRequest request,
                                         YliyunTrustedContext context, String traceId) {
        requireEqual(request, YliyunRuntimeHeaders.TENANT_ID, String.valueOf(context.tenantId()));
        requireEqual(request, YliyunRuntimeHeaders.USER_ID, String.valueOf(context.userId()));
        requireEqual(request, YliyunRuntimeHeaders.WORKSPACE_ID, context.workspaceId());
        requireEqual(request, YliyunRuntimeHeaders.APP_CODE, context.appCode());
        requireEqual(request, YliyunRuntimeHeaders.BIZ_TYPE, context.bizType());
        requireEqual(request, YliyunRuntimeHeaders.BIZ_ID, context.bizId());
        if (context.runId() != null) {
            requireEqual(request, YliyunRuntimeHeaders.RUN_ID, context.runId());
        }
        if (!YliyunRuntimeCrypto.constantTimeUtf8Equals(context.traceId(), traceId)) {
            throw contextFailure("Header traceId does not match Trusted Context");
        }
    }

    private void verifyIdempotency(CachedBodyHttpServletRequest request) {
        String method = request.getMethod();
        if ("POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method)) {
            String key = requiredHeader(request, YliyunRuntimeHeaders.IDEMPOTENCY_KEY);
            if (!key.matches("[A-Za-z0-9._:-]{8,160}")) {
                throw new YliyunRuntimeAuthException(
                        "CONTEXT_MISSING", 400, "Idempotency-Key is invalid",
                        "auth.idempotency",
                        "Use a stable operation-scoped idempotency key", false);
            }
        }
    }

    private void requireEqual(CachedBodyHttpServletRequest request,
                              String header, String expected) {
        String actual = requiredHeader(request, header);
        if (!YliyunRuntimeCrypto.constantTimeUtf8Equals(expected, actual)) {
            throw contextFailure(header + " does not match Trusted Context");
        }
    }


    private String tokenHeader(CachedBodyHttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new YliyunRuntimeAuthException(
                    "AUTH_TOKEN_MISSING", 401, "Missing required service token: " + name,
                    "auth.request_signature",
                    "Configure and send both service authentication tokens", false);
        }
        return value.trim();
    }

    private String requiredHeader(CachedBodyHttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new YliyunRuntimeAuthException(
                    "AUTH_HEADER_MISSING", 401, "Missing required header: " + name,
                    "auth.request_signature",
                    "Send every required Frozen runtime header", false);
        }
        return value.trim();
    }

    private void requireConfigured() {
        if (isBlank(properties.getServiceToken())
                || isBlank(properties.getServiceSecret())
                || (properties.isRequireInternalToken()
                && isBlank(properties.getInternalToken()))) {
            throw new YliyunRuntimeAuthException(
                    "INTERNAL_ERROR", 503,
                    "Yliyun runtime security is enabled but secrets are not configured",
                    "auth.configuration",
                    "Configure service token, internal token and service secret", true);
        }
        if (properties.getSignatureVersion()
                == YliyunRuntimeSecurityProperties.SignatureVersion.FROZEN_V1
                && !properties.isAllowLegacySignature()) {
            throw new YliyunRuntimeAuthException(
                    "INTERNAL_ERROR", 503,
                    "FROZEN_V1 signature mode requires explicit allowLegacySignature",
                    "auth.configuration",
                    "Use CONTEXT_V1_1 or explicitly acknowledge the legacy risk", true);
        }
        if (properties.getServiceSecret().getBytes(StandardCharsets.UTF_8).length < 32
                || properties.getServiceToken().length() < 16
                || (properties.isRequireInternalToken()
                && properties.getInternalToken().length() < 16)) {
            throw new YliyunRuntimeAuthException(
                    "INTERNAL_ERROR", 503, "Configured Yliyun runtime secrets are too short",
                    "auth.configuration", "Use high-entropy service credentials", true);
        }
        if (properties.getMaxBodyBytes() < 1
                || properties.getMaxBodyBytes() > 50 * 1024 * 1024
                || properties.getMaxContextBytes() < 1
                || properties.getMaxContextBytes() > 64 * 1024) {
            throw new YliyunRuntimeAuthException(
                    "INTERNAL_ERROR", 503, "Configured request limits must be positive",
                    "auth.configuration", "Fix MateClaw runtime request limits", true);
        }
        if (properties.getClockSkewSeconds() < 1 || properties.getClockSkewSeconds() > 300) {
            throw new YliyunRuntimeAuthException(
                    "INTERNAL_ERROR", 503, "Configured clock skew must be between 1 and 300 seconds",
                    "auth.configuration", "Fix MateClaw runtime security configuration", true);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static YliyunRuntimeAuthException authFailure(String code, String message) {
        return new YliyunRuntimeAuthException(
                code, 401, message,
                "auth.request_signature",
                "Create a new signed request using the configured service credentials", false);
    }

    private static YliyunRuntimeAuthException contextFailure(String message) {
        return new YliyunRuntimeAuthException(
                "CONTEXT_MISSING", 401, message,
                "auth.context_validation",
                "Regenerate Trusted Context from the cloud backend", false);
    }
}
