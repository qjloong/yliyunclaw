package vip.mate.auth.yliyun;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** current/previous 验签窗口，支持一粒云 ticket 与撤销通知无中断轮换。 */
@Slf4j
@Component
public class YliyunTicketKeyRing {

    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final KeyEntry current;
    private final KeyEntry previous;

    public YliyunTicketKeyRing(
            @Value("${mateclaw.auth.yliyun.ticket-secret-current:${mateclaw.auth.yliyun.ticket-secret}}")
            String currentSecret,
            @Value("${mateclaw.auth.yliyun.ticket-secret-previous:}") String previousSecret,
            @Value("${mateclaw.auth.yliyun.ticket-key-id-current:current}") String currentKeyId,
            @Value("${mateclaw.auth.yliyun.ticket-key-id-previous:previous}") String previousKeyId) {
        this.current = entry(currentKeyId, currentSecret, "current", true);
        this.previous = previousSecret == null || previousSecret.isBlank()
                ? null : entry(previousKeyId, previousSecret, "previous", true);
    }

    @PostConstruct
    void validate() {
        if (previous != null) {
            if (Objects.equals(current.id(), previous.id())) {
                throw new IllegalStateException("Yliyun current/previous ticket key id 不能相同");
            }
            if (MessageDigest.isEqual(current.bytes(), previous.bytes())) {
                throw new IllegalStateException("Yliyun current/previous ticket 密钥不能相同");
            }
        }
        log.info("[Yliyun Ticket Key Ring] currentKeyId={}, previousConfigured={}, previousKeyId={}",
                current.id(), previous != null, previous == null ? "none" : previous.id());
    }

    public List<VerificationKey> verificationKeys(String preferredKeyId) {
        List<KeyEntry> ordered = orderedEntries(preferredKeyId);
        return ordered.stream().map(entry -> new VerificationKey(entry.id(),
                Keys.hmacShaKeyFor(entry.bytes()))).toList();
    }

    /** 返回完成验签的 key id；失败返回 null。 */
    public String verifyBase64Url(String payload, String signature, String preferredKeyId) {
        byte[] supplied;
        try {
            supplied = Base64.getUrlDecoder().decode(signature);
        } catch (Exception ex) {
            return null;
        }
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        for (KeyEntry entry : orderedEntries(preferredKeyId)) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(entry.bytes(), "HmacSHA256"));
                if (MessageDigest.isEqual(supplied, mac.doFinal(payloadBytes))) {
                    return entry.id();
                }
            } catch (Exception ex) {
                throw new IllegalStateException("无法验证 Yliyun ticket 签名", ex);
            }
        }
        return null;
    }

    public boolean previousConfigured() {
        return previous != null;
    }

    private List<KeyEntry> orderedEntries(String preferredKeyId) {
        List<KeyEntry> entries = new ArrayList<>(2);
        if (preferredKeyId != null && !preferredKeyId.isBlank()) {
            if (current.id().equals(preferredKeyId)) {
                entries.add(current);
            } else if (previous != null && previous.id().equals(preferredKeyId)) {
                entries.add(previous);
            }
        }
        if (!entries.contains(current)) {
            entries.add(current);
        }
        if (previous != null && !entries.contains(previous)) {
            entries.add(previous);
        }
        return entries;
    }

    private KeyEntry entry(String keyId, String secret, String slot, boolean required) {
        String normalizedId = keyId == null ? "" : keyId.trim();
        String normalizedSecret = secret == null ? "" : secret.trim();
        if (!KEY_ID_PATTERN.matcher(normalizedId).matches()) {
            throw new IllegalStateException("Yliyun " + slot + " ticket key id 格式无效");
        }
        byte[] bytes = normalizedSecret.getBytes(StandardCharsets.UTF_8);
        if (required && bytes.length < 32) {
            throw new IllegalStateException("Yliyun " + slot + " ticket 密钥至少需要 32 字节");
        }
        return new KeyEntry(normalizedId, bytes);
    }

    public record VerificationKey(String keyId, SecretKey secretKey) {
    }

    private record KeyEntry(String id, byte[] bytes) {
    }
}
