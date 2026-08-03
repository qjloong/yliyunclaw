package vip.mate.auth.yliyun;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YliyunTicketKeyRingTest {

    private static final String CURRENT = "current-ticket-secret-that-is-at-least-32-bytes";
    private static final String PREVIOUS = "previous-ticket-secret-that-is-at-least-32-bytes";
    private static final String PAYLOAD = "tenant=135\nconfigVersion=30";

    @Test
    void verifiesCurrentAndPreviousDuringRotationWindow() throws Exception {
        YliyunTicketKeyRing ring = new YliyunTicketKeyRing(
                CURRENT, PREVIOUS, "key-2026-08", "key-2026-07");
        ring.validate();

        assertEquals("key-2026-08", ring.verifyBase64Url(
                PAYLOAD, sign(PAYLOAD, CURRENT), "key-2026-08"));
        assertEquals("key-2026-07", ring.verifyBase64Url(
                PAYLOAD, sign(PAYLOAD, PREVIOUS), "key-2026-07"));
        assertTrue(ring.previousConfigured());
        assertEquals(2, ring.verificationKeys(null).size());
    }

    @Test
    void rejectsUnknownSignature() {
        YliyunTicketKeyRing ring = new YliyunTicketKeyRing(
                CURRENT, PREVIOUS, "current", "previous");
        ring.validate();

        assertNull(ring.verifyBase64Url(PAYLOAD, "invalid", null));
    }

    @Test
    void failsFastForShortOrReusedSecrets() {
        assertThrows(IllegalStateException.class,
                () -> new YliyunTicketKeyRing("short", "", "current", "previous"));

        YliyunTicketKeyRing reused = new YliyunTicketKeyRing(
                CURRENT, CURRENT, "current", "previous");
        assertThrows(IllegalStateException.class, reused::validate);
    }

    private String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
