package vip.mate.auth.yliyun;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vip.mate.auth.service.AuthService;
import vip.mate.exception.MateClawException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class YliyunAuthControllerContractTest {

    private static final String SECRET = "contract-test-ticket-secret-at-least-32-bytes";
    private static final String STATE = "state-contract";
    private static final String NONCE = "nonce-contract";
    private static final String PARENT_ORIGIN = "http://localhost:8080";

    private YliyunAuthController controller;
    private YliyunUserMappingService mappingService;
    private AuthService authService;
    private YliyunTicketReplayService replayService;

    @BeforeEach
    void setUp() {
        mappingService = mock(YliyunUserMappingService.class);
        authService = mock(AuthService.class);
        replayService = mock(YliyunTicketReplayService.class);
        YliyunTicketKeyRing keyRing = new YliyunTicketKeyRing(
                SECRET, "", "current", "previous");
        keyRing.validate();
        controller = new YliyunAuthController(mappingService, authService, replayService,
                mock(YliyunAuthCookieService.class), keyRing);
        ReflectionTestUtils.setField(controller, "ticketIssuer", "yliyun");
        ReflectionTestUtils.setField(controller, "ticketAudience", "mateclaw");
        ReflectionTestUtils.setField(controller, "appKey", "mateclaw_ai_assistant");
    }

    @Test
    void shouldRejectForgedAudienceBeforeReplayOrProvisioning() throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("aud", "other-service");

        MateClawException error = assertThrows(MateClawException.class,
                () -> redeem(sign(claims)));

        assertEquals(401, error.getCode());
        assertEquals("err.auth.yliyun.invalid_aud", error.getMsgKey());
        verifyNoInteractions(replayService, mappingService, authService);
    }

    @Test
    void shouldRejectForgedAppKeyBeforeReplayOrProvisioning() throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("appKey", "wenshu_integration");

        MateClawException error = assertThrows(MateClawException.class,
                () -> redeem(sign(claims)));

        assertEquals(401, error.getCode());
        assertEquals("err.auth.yliyun.invalid_appKey", error.getMsgKey());
        verifyNoInteractions(replayService, mappingService, authService);
    }

    @Test
    void shouldRejectClaimedKeyIdThatDoesNotMatchVerifiedKey() throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("kid", "previous");

        MateClawException error = assertThrows(MateClawException.class,
                () -> redeem(sign(claims)));

        assertEquals(401, error.getCode());
        assertEquals("err.auth.yliyun.invalid_kid", error.getMsgKey());
        verifyNoInteractions(replayService, mappingService, authService);
    }

    private void redeem(String ticket) {
        controller.ticket(ticket, "/chat", null, null, null, null,
                null, null, PARENT_ORIGIN, STATE, NONCE, null,
                mock(HttpServletRequest.class));
    }

    private Map<String, Object> validClaims() {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "yliyun");
        claims.put("aud", "mateclaw");
        claims.put("appKey", "mateclaw_ai_assistant");
        claims.put("state", STATE);
        claims.put("nonce", NONCE);
        claims.put("parentOrigin", PARENT_ORIGIN);
        claims.put("tenantId", 1);
        claims.put("userId", 100);
        claims.put("configVersion", 23);
        claims.put("jti", "contract-jti");
        claims.put("iat", now);
        claims.put("exp", now + 60);
        claims.put("launchContext", Map.of());
        claims.put("kid", "current");
        return claims;
    }

    private String sign(Map<String, Object> claims) throws Exception {
        String payload = new ObjectMapper().writeValueAsString(claims);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        return encodedPayload + "." + signature;
    }
}
