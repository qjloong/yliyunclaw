package vip.mate.auth.yliyun;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.exception.MateClawException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Yliyun 票据认证控制器
 * <p>
 * 接收 Yliyun 平台签发的 JWT ticket，验证通过后签发 MateClaw JWT token 并重定向。
 * Yliyun → MateClaw 的单点登录入口。
 *
 * @author MateClaw Team
 */
@Tag(name = "Yliyun 票据认证")
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/yliyun")
@RequiredArgsConstructor
public class YliyunAuthController {

    private final YliyunUserMappingService userMappingService;
    private final AuthService authService;

    @Value("${mateclaw.auth.yliyun.ticket-secret}")
    private String ticketSecret;

    /** 防重放 nonce 缓存：jti → 过期时间戳（秒），ticket 5 分钟内有效 */
    private final ConcurrentHashMap<String, Long> usedNonces = new ConcurrentHashMap<>();

    {
        // 后台线程定期清理过期 nonce（每 5 分钟）
        Thread cleanup = new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.MINUTES.sleep(5);
                    long now = System.currentTimeMillis() / 1000;
                    usedNonces.entrySet().removeIf(e -> e.getValue() < now);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "yliyun-nonce-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    /**
     * Yliyun ticket 认证入口。
     * <p>
     * 验证 Yliyun 签发的 JWT ticket，查找或创建对应的 MateClaw 用户，
     * 签发 MateClaw JWT token，最后重定向到前端地址。
     *
     * @param ticket   Yliyun 签发的 JWT ticket
     * @param redirect 认证成功后的重定向地址（默认 /chat）
     * @return 302 重定向，附带 token 参数
     */
    @Operation(summary = "Yliyun ticket 认证（302 重定向）")
    @GetMapping("/ticket")
    public ResponseEntity<Void> ticket(
            @RequestParam String ticket,
            @RequestParam(defaultValue = "/chat") String redirect,
            @RequestParam(required = false) String fileId,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String folderId,
            @RequestParam(required = false) String folderName,
            jakarta.servlet.http.HttpServletRequest request) {

        // 1. 验证 ticket
        Map<String, Object> claims = verifyTicket(ticket);

        // 2. 提取声明
        String yliyunUserId = String.valueOf(claims.get("userId"));
        String yliyunTenantId = String.valueOf(claims.getOrDefault("tenantId", "1"));
        String nickname = claims.get("nickname") instanceof String ? (String) claims.get("nickname") : "User";

        if (yliyunUserId == null || yliyunUserId.isBlank()) {
            throw new MateClawException("err.auth.yliyun.missing_user_id", 400, "ticket 中缺少 userId");
        }

        log.info("[Yliyun] Ticket verified: userId={}, tenantId={}", yliyunUserId, yliyunTenantId);

        // 3. 防重放：检查 ticket jti 是否已被使用
        Object jti = claims.get("jti");
        long nowSec = System.currentTimeMillis() / 1000;
        if (jti instanceof String && !((String) jti).isBlank()) {
            Long prevUsed = usedNonces.putIfAbsent((String) jti, nowSec + 300);
            if (prevUsed != null) {
                log.warn("[Yliyun] Replay attack detected: jti={}, userId={}", jti, yliyunUserId);
                throw new MateClawException("err.auth.yliyun.replay", 401, "ticket 已被使用（疑似重放）");
            }
        }

        // 4. 查找或创建用户
        UserEntity user = userMappingService.findOrCreateUser(yliyunUserId, yliyunTenantId, nickname);

        // 5. 签发 MateClaw JWT
        String token = authService.generateToken(user);

        // 6. 构建重定向 URL（保留文件上下文参数）
        StringBuilder url = new StringBuilder(redirect);
        url.append(redirect.contains("?") ? "&" : "?").append("token=").append(token);
        appendParam(url, "fileId", fileId);
        appendParam(url, "fileName", fileName);
        appendParam(url, "folderId", folderId);
        appendParam(url, "folderName", folderName);

        log.info("[Yliyun] Auth success: yliyunUserId={} → mateUserId={}, url={}",
                yliyunUserId, user.getId(), url);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(url.toString()));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    private void appendParam(StringBuilder url, String name, String value) {
        if (value != null && !value.isBlank()) {
            url.append("&").append(name).append("=").append(value);
        }
    }

    /**
     * 验证 ticket：支持 JWT 格式和简化 HMAC 格式。
     *
     * JWT 格式: header.payload.signature
     * HMAC 格式: base64url(payload).base64url(signature)
     */
    private Map<String, Object> verifyTicket(String ticket) {
        // 先尝试 JWT 格式
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getTicketSignKey())
                    .build()
                    .parseSignedClaims(ticket)
                    .getPayload();
            return Map.of(
                "userId", String.valueOf(claims.get("userId", Long.class)),
                "tenantId", String.valueOf(claims.get("tenantId", Long.class)),
                "nickname", claims.get("nickname", String.class)
            );
        } catch (Exception e) {
            log.debug("[Yliyun] Not a JWT ticket, trying HMAC format: {}", e.getMessage());
        }

        // 尝试简化 HMAC 格式: payload.sig
        try {
            String[] parts = ticket.split("\\.", 2);
            if (parts.length != 2) {
                throw new MateClawException("err.auth.yliyun.invalid_ticket", 401, "ticket 格式无效");
            }

            // 验证 HMAC 签名
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            byte[] expectedSig = Base64.getUrlDecoder().decode(parts[1]);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                ticketSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] actualSig = mac.doFinal(payloadBytes);

            if (!java.security.MessageDigest.isEqual(expectedSig, actualSig)) {
                throw new MateClawException("err.auth.yliyun.invalid_ticket", 401, "ticket 签名无效");
            }

            // 解析 JSON payload
            String json = new String(payloadBytes, StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(json, Map.class);

            // 检查过期
            Object expObj = map.get("exp");
            long exp = expObj instanceof Number ? ((Number) expObj).longValue() : 0;
            if (exp > 0 && System.currentTimeMillis() / 1000 > exp) {
                throw new MateClawException("err.auth.yliyun.expired_ticket", 401, "ticket 已过期");
            }

            return map;
        } catch (MateClawException ex) {
            throw ex;
        } catch (Exception e) {
            log.warn("[Yliyun] Ticket verification failed: {}", e.getMessage());
            throw new MateClawException("err.auth.yliyun.invalid_ticket", 401, "ticket 验证失败");
        }
    }

    /**
     * 获取 ticket 签名密钥（HMAC-SHA256，至少 32 字节）。
     */
    private SecretKey getTicketSignKey() {
        byte[] keyBytes = ticketSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
