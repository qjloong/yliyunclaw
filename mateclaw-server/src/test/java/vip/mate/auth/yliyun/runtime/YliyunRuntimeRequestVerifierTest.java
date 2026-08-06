package vip.mate.auth.yliyun.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class YliyunRuntimeRequestVerifierTest {

    private static final String TOKEN = "service-token-for-tests";
    private static final String INTERNAL = "internal-token-for-tests";
    private static final String SECRET = "test-service-secret-32-bytes-long!";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final YliyunRuntimeNonceService nonceService = mock(YliyunRuntimeNonceService.class);
    private final YliyunTrustedContextCrossValidator crossValidator =
            mock(YliyunTrustedContextCrossValidator.class);
    private YliyunRuntimeSecurityProperties properties;
    private YliyunRuntimeRequestVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new YliyunRuntimeSecurityProperties();
        properties.setServiceToken(TOKEN);
        properties.setInternalToken(INTERNAL);
        properties.setServiceSecret(SECRET);
        verifier = new YliyunRuntimeRequestVerifier(
                objectMapper, properties, nonceService, crossValidator);
    }

    @Test
    void acceptsSignedRequestAndConsumesNonceAfterVerification() throws Exception {
        String nonce = "nonce-123456";
        CachedBodyHttpServletRequest request = signedRequest(nonce);
        YliyunTrustedContext result = verifier.verify(request);
        assertEquals(1001, result.tenantId());
        verify(nonceService).consume(nonce);
        verify(crossValidator).validate(result);
    }

    @Test
    void rejectsHeaderTampering() throws Exception {
        CachedBodyHttpServletRequest request = signedRequest("nonce-123457");
        request.setAttribute("unused", "unused");
        MockHttpServletRequest source = (MockHttpServletRequest) request.getRequest();
        source.removeHeader(YliyunRuntimeHeaders.BIZ_ID);
        source.addHeader(YliyunRuntimeHeaders.BIZ_ID, "9999");

        YliyunRuntimeAuthException ex = assertThrows(
                YliyunRuntimeAuthException.class, () -> verifier.verify(request));
        assertEquals("CONTEXT_MISSING", ex.getErrorCode());
        verify(nonceService, never()).consume("nonce-123457");
    }

    @Test
    void invalidSignatureCannotPoisonNonceStore() throws Exception {
        CachedBodyHttpServletRequest request = signedRequest("nonce-123458");
        MockHttpServletRequest source = (MockHttpServletRequest) request.getRequest();
        source.removeHeader(YliyunRuntimeHeaders.SIGNATURE);
        source.addHeader(YliyunRuntimeHeaders.SIGNATURE, "YWJj");

        YliyunRuntimeAuthException ex = assertThrows(
                YliyunRuntimeAuthException.class, () -> verifier.verify(request));
        assertEquals("AUTH_SIGNATURE_MISMATCH", ex.getErrorCode());
        verify(nonceService, never()).consume("nonce-123458");
    }

    private CachedBodyHttpServletRequest signedRequest(String nonce) throws Exception {
        long now = Instant.now().getEpochSecond();
        YliyunTrustedContext context = new YliyunTrustedContext(
                1001, 2001, "ws_10", "GOAL", "GOAL_PROJECT", "3001",
                null, "run_x1", "550e8400-e29b-41d4-a716-446655440000",
                List.of("OWNER"), List.of("PROJECT_READ"), 12,
                now - 1, now + 59);
        byte[] contextBytes = objectMapper.writeValueAsBytes(context);
        String contextHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(contextBytes);
        String contextHash = YliyunRuntimeCrypto.sha256Hex(contextBytes);
        byte[] body = "{\"operationType\":\"TASK_EXECUTION\"}"
                .getBytes(StandardCharsets.UTF_8);

        MockHttpServletRequest source = new MockHttpServletRequest(
                "POST", "/api/internal/v1/runs");
        source.setContent(body);
        source.setContentType("application/json");
        source.addHeader("Authorization", "Bearer " + TOKEN);
        source.addHeader(YliyunRuntimeHeaders.INTERNAL_TOKEN, INTERNAL);
        source.addHeader(YliyunRuntimeHeaders.TENANT_ID, "1001");
        source.addHeader(YliyunRuntimeHeaders.USER_ID, "2001");
        source.addHeader(YliyunRuntimeHeaders.WORKSPACE_ID, "ws_10");
        source.addHeader(YliyunRuntimeHeaders.APP_CODE, "GOAL");
        source.addHeader(YliyunRuntimeHeaders.BIZ_TYPE, "GOAL_PROJECT");
        source.addHeader(YliyunRuntimeHeaders.BIZ_ID, "3001");
        source.addHeader(YliyunRuntimeHeaders.RUN_ID, "run_x1");
        source.addHeader(YliyunRuntimeHeaders.TRACE_ID,
                "550e8400-e29b-41d4-a716-446655440000");
        source.addHeader(YliyunRuntimeHeaders.TIMESTAMP, String.valueOf(now));
        source.addHeader(YliyunRuntimeHeaders.NONCE, nonce);
        source.addHeader(YliyunRuntimeHeaders.TRUSTED_CONTEXT, contextHeader);
        source.addHeader(YliyunRuntimeHeaders.CONTEXT_SHA256, contextHash);
        source.addHeader(YliyunRuntimeHeaders.IDEMPOTENCY_KEY, "run-3001-0001");

        String canonical = YliyunRuntimeCrypto.canonicalV11(
                "POST", "/api/internal/v1/runs", null,
                String.valueOf(now), nonce, contextHash,
                YliyunRuntimeCrypto.sha256Hex(body));
        source.addHeader(YliyunRuntimeHeaders.SIGNATURE,
                YliyunRuntimeCrypto.hmacBase64(
                        SECRET, canonical.getBytes(StandardCharsets.UTF_8)));
        return new CachedBodyHttpServletRequest(source, properties.getMaxBodyBytes());
    }
}
