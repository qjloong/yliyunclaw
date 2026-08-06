package vip.mate.auth.yliyun.runtime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YliyunRuntimeCryptoTest {

    private static final String SECRET = "test-service-secret-32-bytes-long!";

    @Test
    void reproducesTheReviewedFrozenV1RawCanonicalVector() {
        byte[] body = "{\"bizType\":\"GOAL_PROJECT\",\"bizId\":\"3001\"}"
                .getBytes(StandardCharsets.UTF_8);
        String bodyHash = YliyunRuntimeCrypto.sha256Hex(body);
        assertEquals("827eb48489826371e325f7e087d1c06b94430e6f5ad7ae743950382787ff0a4c",
                bodyHash);

        String canonical = YliyunRuntimeCrypto.canonicalV1(
                "POST", "/api/internal/v1/runs", null,
                "1785740000", "n1q2x3z4", bodyHash);
        assertEquals("UH9wUvMBYA2ZYnrlST0EMADEc1osKscpTUX80qT5PhY=",
                YliyunRuntimeCrypto.hmacBase64(
                        SECRET, canonical.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void bindsTrustedContextDigestInV11Canonical() {
        byte[] context = ("{\"tenantId\":1001,\"userId\":2001,"
                + "\"workspaceId\":\"ws_10\",\"appCode\":\"GOAL\","
                + "\"bizType\":\"GOAL_PROJECT\",\"bizId\":\"3001\","
                + "\"conversationId\":null,\"runId\":\"run_x1\","
                + "\"traceId\":\"550e8400-e29b-41d4-a716-446655440000\","
                + "\"roles\":[\"OWNER\"],\"capabilities\":[\"PROJECT_READ\"],"
                + "\"sourceVersion\":12,\"issuedAt\":1785740000,"
                + "\"expiresAt\":1785740060}").getBytes(StandardCharsets.UTF_8);
        byte[] body = "{\"bizType\":\"GOAL_PROJECT\",\"bizId\":\"3001\"}"
                .getBytes(StandardCharsets.UTF_8);
        String canonical = YliyunRuntimeCrypto.canonicalV11(
                "POST", "/api/internal/v1/runs", "b=2&a=1",
                "1785740000", "n1q2x3z4",
                YliyunRuntimeCrypto.sha256Hex(context),
                YliyunRuntimeCrypto.sha256Hex(body));
        assertEquals("pHusRuW4ftiQCxSyZLuKLRL70l+Hby7V/Bf/xK48LdY=",
                YliyunRuntimeCrypto.hmacBase64(
                        SECRET, canonical.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void canonicalQueryPreservesRawComponentsAndSortsThem() {
        assertEquals("a=1&a=2&b=%E4%B8%AD", YliyunRuntimeCrypto.canonicalQuery(
                "b=%E4%B8%AD&a=2&a=1"));
    }

    @Test
    void comparisonsAreConstantTimeFriendlyAndRejectInvalidBase64() {
        assertTrue(YliyunRuntimeCrypto.constantTimeUtf8Equals("abc", "abc"));
        assertFalse(YliyunRuntimeCrypto.constantTimeUtf8Equals("abc", "abd"));
        assertTrue(YliyunRuntimeCrypto.constantTimeBase64Equals("YWJj", "YWJj"));
        assertFalse(YliyunRuntimeCrypto.constantTimeBase64Equals("YWJj", "%%%"));
    }
}
