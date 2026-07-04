package vip.mate.tool.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneratedFileDiskTokenService {
    private static final char TOKEN_SEPARATOR = '~';

    private final ObjectMapper objectMapper;

    @Value("${mate.generated-files.token-secret:mateclaw-generated-file-secret}")
    private String tokenSecret;

    @Value("${mate.generated-files.token-ttl-seconds:604800}")
    private long tokenTtlSeconds;

    public record DiskToken(Path path,
                            Long workspaceId,
                            String conversationId,
                            String workspaceBasePath,
                            long expiresAt) {
    }

    public String issue(Path absolutePath) {
        return issue(absolutePath, null, null, null);
    }

    public String issue(Path absolutePath,
                        Long workspaceId,
                        String conversationId,
                        String workspaceBasePath) {
        try {
            long expireAt = System.currentTimeMillis() + Duration.ofSeconds(Math.max(60, tokenTtlSeconds)).toMillis();
            java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("path", absolutePath.toAbsolutePath().normalize().toString());
            payload.put("exp", expireAt);
            if (workspaceId != null) payload.put("workspaceId", workspaceId);
            if (conversationId != null && !conversationId.isBlank()) payload.put("conversationId", conversationId);
            if (workspaceBasePath != null && !workspaceBasePath.isBlank()) payload.put("workspaceBasePath", workspaceBasePath);
            byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);
            String payloadPart = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
            String signaturePart = sign(payloadPart);
            return payloadPart + TOKEN_SEPARATOR + signaturePart;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue generated file disk token", e);
        }
    }

    public Optional<Path> verifyAndResolve(String token) {
        return verify(token).map(DiskToken::path);
    }

    public Optional<DiskToken> verify(String token) {
        try {
            if (token == null || token.isBlank()) return Optional.empty();
            int sep = findTokenSeparator(token);
            if (sep <= 0 || sep >= token.length() - 1) return Optional.empty();

            String payloadPart = token.substring(0, sep);
            String signaturePart = token.substring(sep + 1);
            String expected = sign(payloadPart);
            if (!constantTimeEquals(expected, signaturePart)) {
                return Optional.empty();
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadPart);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, Map.class);
            Object expObj = payload.get("exp");
            Object pathObj = payload.get("path");
            if (!(expObj instanceof Number) || !(pathObj instanceof String pathText) || pathText.isBlank()) {
                return Optional.empty();
            }
            long exp = ((Number) expObj).longValue();
            if (System.currentTimeMillis() > exp) {
                return Optional.empty();
            }
            Long workspaceId = payload.get("workspaceId") instanceof Number n ? n.longValue() : null;
            String conversationId = payload.get("conversationId") instanceof String cid ? cid : null;
            String workspaceBasePath = payload.get("workspaceBasePath") instanceof String base ? base : null;
            return Optional.of(new DiskToken(
                    Path.of(pathText).toAbsolutePath().normalize(),
                    workspaceId,
                    conversationId,
                    workspaceBasePath,
                    exp));
        } catch (Exception e) {
            log.debug("Generated file token verify failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private int findTokenSeparator(String token) {
        int sep = token.lastIndexOf(TOKEN_SEPARATOR);
        if (sep > 0) {
            return sep;
        }
        // Backward compatibility for links issued before the path-safe token format.
        return token.lastIndexOf('.');
    }

    private String sign(String payloadPart) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int result = 0;
        for (int i = 0; i < x.length; i++) {
            result |= x[i] ^ y[i];
        }
        return result == 0;
    }
}
