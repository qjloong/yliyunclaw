package vip.mate.auth.yliyun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vip.mate.common.result.R;
import vip.mate.exception.MateClawException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class YliyunSessionRevocationControllerTest {

    private static final String CURRENT_SECRET = "test-current-revocation-secret-long-enough";
    private static final String PREVIOUS_SECRET = "test-previous-revocation-secret-long-enough";

    private YliyunUserMappingService mappingService;
    private YliyunTicketReplayService replayService;
    private YliyunSessionRevocationController controller;

    @BeforeEach
    void setUp() {
        mappingService = mock(YliyunUserMappingService.class);
        replayService = mock(YliyunTicketReplayService.class);
        YliyunTicketKeyRing keyRing = new YliyunTicketKeyRing(
                CURRENT_SECRET, PREVIOUS_SECRET, "current", "previous");
        keyRing.validate();
        controller = new YliyunSessionRevocationController(mappingService, replayService, keyRing);
        ReflectionTestUtils.setField(controller, "expectedAppKey", "mateclaw_ai_assistant");
    }

    @Test
    void acceptsSignedRevocationAndAdvancesTenantMappings() throws Exception {
        YliyunSessionRevocationRequest request = request();
        when(mappingService.revokeTenantEntitlement("135", "mateclaw_ai_assistant", 12))
                .thenReturn(3);

        R<Map<String, Object>> response = controller.revoke(
                sign(request, CURRENT_SECRET), "current", request);

        assertEquals(200, response.getCode());
        assertEquals(3, response.getData().get("revokedMappings"));
        verify(replayService).consume("revoke-" + request.getNonce());
        verify(mappingService).revokeTenantEntitlement("135", "mateclaw_ai_assistant", 12);
    }

    @Test
    void acceptsPreviousKeyDuringRotationWindow() throws Exception {
        YliyunSessionRevocationRequest request = request();
        when(mappingService.revokeTenantEntitlement("135", "mateclaw_ai_assistant", 12))
                .thenReturn(1);

        R<Map<String, Object>> response = controller.revoke(
                sign(request, PREVIOUS_SECRET), "previous", request);

        assertEquals(200, response.getCode());
        verify(mappingService).revokeTenantEntitlement("135", "mateclaw_ai_assistant", 12);
    }

    @Test
    void deactivatesApplicationWithOperationBoundSignature() throws Exception {
        YliyunSessionRevocationRequest request = request();
        when(mappingService.deactivateTenantApplication(
                "135", "mateclaw_ai_assistant", 12))
                .thenReturn(new YliyunUserMappingService.TenantApplicationDeactivation(3, 1, 1));

        R<Map<String, Object>> response = controller.deactivate(
                signDeactivate(request, CURRENT_SECRET), "current", request);

        assertEquals(200, response.getCode());
        assertEquals(3, response.getData().get("revokedMappings"));
        assertEquals(1, response.getData().get("retainedWorkspaces"));
        assertEquals(1, response.getData().get("deactivatedAgents"));
        verify(replayService).consume("deactivate-" + request.getNonce());
        verify(mappingService).deactivateTenantApplication(
                "135", "mateclaw_ai_assistant", 12);
    }

    @Test
    void rejectsLegacyRevokeSignatureOnDeactivationEndpoint() throws Exception {
        YliyunSessionRevocationRequest request = request();

        MateClawException error = assertThrows(MateClawException.class,
                () -> controller.deactivate(
                        sign(request, CURRENT_SECRET), "current", request));

        assertEquals(401, error.getCode());
        verifyNoInteractions(replayService, mappingService);
    }

    @Test
    void rejectsInvalidSignatureBeforeChangingMappings() {
        YliyunSessionRevocationRequest request = request();

        MateClawException error = assertThrows(MateClawException.class,
                () -> controller.revoke("invalid", null, request));

        assertEquals(401, error.getCode());
        verifyNoInteractions(replayService, mappingService);
    }

    @Test
    void rejectsSignedRevocationForAnotherApplication() throws Exception {
        YliyunSessionRevocationRequest request = request();
        request.setAppKey("another_application");

        MateClawException error = assertThrows(MateClawException.class,
                () -> controller.revoke(sign(request, CURRENT_SECRET), "current", request));

        assertEquals(401, error.getCode());
        verifyNoInteractions(replayService, mappingService);
    }

    private YliyunSessionRevocationRequest request() {
        YliyunSessionRevocationRequest request = new YliyunSessionRevocationRequest();
        request.setAppKey("mateclaw_ai_assistant");
        request.setTenantId("135");
        request.setConfigVersion(12);
        request.setIssuedAt(Instant.now().getEpochSecond());
        request.setNonce("revocation-nonce-1234567890");
        return request;
    }

    private String sign(YliyunSessionRevocationRequest request, String secret) throws Exception {
        String payload = String.join("\n", request.getAppKey(), request.getTenantId(),
                String.valueOf(request.getConfigVersion()), String.valueOf(request.getIssuedAt()),
                request.getNonce());
        return signPayload(payload, secret);
    }

    private String signDeactivate(YliyunSessionRevocationRequest request, String secret) throws Exception {
        String payload = "DEACTIVATE\n" + String.join("\n", request.getAppKey(), request.getTenantId(),
                String.valueOf(request.getConfigVersion()), String.valueOf(request.getIssuedAt()),
                request.getNonce());
        return signPayload(payload, secret);
    }

    private String signPayload(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
