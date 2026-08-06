package vip.mate.auth.yliyun.runtime;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.stream.Collectors;

/** Pure-JDK canonicalization and cryptographic helpers. */
public final class YliyunRuntimeCrypto {

    private YliyunRuntimeCrypto() {
    }

    public static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /**
     * Sorts raw query components without decoding and re-encoding them. Both sides
     * must sign the exact same request-target representation.
     */
    public static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        return Arrays.stream(rawQuery.split("&", -1))
                .sorted()
                .collect(Collectors.joining("&"));
    }

    public static String canonicalV1(String method, String path, String rawQuery,
                                     String timestamp, String nonce, String bodyHash) {
        return String.join("\n",
                method.toUpperCase(), path, canonicalQuery(rawQuery),
                timestamp, nonce, bodyHash);
    }

    public static String canonicalV11(String method, String path, String rawQuery,
                                      String timestamp, String nonce,
                                      String contextHash, String bodyHash) {
        return String.join("\n",
                method.toUpperCase(), path, canonicalQuery(rawQuery),
                timestamp, nonce, contextHash, bodyHash);
    }

    public static String hmacBase64(String secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is unavailable", ex);
        }
    }

    public static byte[] legacyMessageBytes(
            String canonical, YliyunRuntimeSecurityProperties.LegacyHmacInput mode) {
        byte[] raw = canonical.getBytes(StandardCharsets.UTF_8);
        return mode == YliyunRuntimeSecurityProperties.LegacyHmacInput.BASE64_CANONICAL
                ? Base64.getEncoder().encode(raw) : raw;
    }

    public static byte[] decodeBase64Url(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new YliyunRuntimeAuthException(
                    "CONTEXT_MISSING", 401,
                    "X-Yly-Trusted-Context is not valid base64url",
                    "auth.context_validation",
                    "Regenerate Trusted Context from the cloud backend", false);
        }
    }

    public static boolean constantTimeUtf8Equals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean constantTimeBase64Equals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        try {
            return MessageDigest.isEqual(
                    Base64.getDecoder().decode(expected),
                    Base64.getDecoder().decode(actual));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
