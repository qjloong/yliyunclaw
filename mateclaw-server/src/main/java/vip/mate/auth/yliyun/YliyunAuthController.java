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
import org.springframework.web.util.UriComponentsBuilder;
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
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final YliyunTicketReplayService replayService;
    private final YliyunAuthCookieService cookieService;

    @Value("${mateclaw.auth.yliyun.ticket-secret}")
    private String ticketSecret;

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
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) String parentOrigin,
            jakarta.servlet.http.HttpServletRequest request) {

        // 1. 验证 ticket
        Map<String, Object> claims = verifyTicket(ticket);

        // 2. 提取声明
        String yliyunUserId = claimString(claims, "userId");
        String yliyunTenantId = claimString(claims, "tenantId");
        String nickname = optionalClaimString(claims, "nickname");
        String account = optionalClaimString(claims, "account");
        String tenantName = optionalClaimString(claims, "tenantName");
        Boolean tenantAdmin = optionalClaimBoolean(claims, "tenantAdmin");

        if (yliyunUserId == null || yliyunUserId.isBlank()) {
            throw new MateClawException("err.auth.yliyun.missing_user_id", 400, "ticket 中缺少 userId");
        }

        log.info("[Yliyun] Ticket verified: userId={}, tenantId={}", yliyunUserId, yliyunTenantId);

        // 3. 防重放：检查 ticket jti 是否已被使用
        replayService.consume(claimString(claims, "jti"));

        // 4. 查找或创建用户
        UserEntity user = userMappingService.findOrCreateUser(
                yliyunUserId, yliyunTenantId, nickname, account, tenantName, tenantAdmin);

        // 5. 签发 MateClaw JWT
        String token = authService.generateToken(user);

        // 6. JWT 只写 HttpOnly Cookie；重定向 URL 只保留非敏感文件上下文。
        String safeRedirect = validateRedirect(redirect);
        UriComponentsBuilder redirectBuilder = UriComponentsBuilder.fromPath(safeRedirect);
        appendParam(redirectBuilder, "fileId", fileId);
        appendParam(redirectBuilder, "fileName", fileName);
        appendParam(redirectBuilder, "folderId", folderId);
        appendParam(redirectBuilder, "folderName", folderName);
        appendConversationId(redirectBuilder, conversationId);
        appendChannelId(redirectBuilder, channelId);
        appendParentOrigin(redirectBuilder, parentOrigin);
        redirectBuilder.queryParam("authSource", "yliyun");
        String url = redirectBuilder.build().encode().toUriString();

        log.info("[Yliyun] Auth success: tenantId={}, yliyunUserId={} → mateUserId={}, redirect={}",
                yliyunTenantId, yliyunUserId, user.getId(), safeRedirect);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(url));
        headers.add(HttpHeaders.SET_COOKIE, cookieService.headerValue(token));
        headers.setCacheControl("no-store, no-cache, must-revalidate");
        headers.setPragma("no-cache");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    private void appendParam(UriComponentsBuilder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            builder.queryParam(name, value);
        }
    }

    private void appendChannelId(UriComponentsBuilder builder, String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        if (!channelId.matches("[a-zA-Z0-9-]{16,128}")) {
            throw new MateClawException("err.auth.yliyun.invalid_channel",
                    400, "channelId 格式无效");
        }
        builder.queryParam("channelId", channelId);
    }

    private void appendConversationId(UriComponentsBuilder builder, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        if (!conversationId.matches("[a-zA-Z0-9_-]{1,128}")) {
            throw new MateClawException("err.auth.yliyun.invalid_conversation",
                    400, "conversationId 格式无效");
        }
        builder.queryParam("conversationId", conversationId);
    }

    private void appendParentOrigin(UriComponentsBuilder builder, String parentOrigin) {
        if (parentOrigin == null || parentOrigin.isBlank()) {
            return;
        }
        URI origin;
        try {
            origin = URI.create(parentOrigin.trim());
        } catch (IllegalArgumentException e) {
            throw new MateClawException("err.auth.yliyun.invalid_parent_origin",
                    400, "parentOrigin 格式无效");
        }
        if ((!"http".equalsIgnoreCase(origin.getScheme())
                && !"https".equalsIgnoreCase(origin.getScheme()))
                || origin.getHost() == null
                || origin.getUserInfo() != null
                || (origin.getPath() != null && !origin.getPath().isBlank())
                || origin.getQuery() != null
                || origin.getFragment() != null) {
            throw new MateClawException("err.auth.yliyun.invalid_parent_origin",
                    400, "parentOrigin 格式无效");
        }
        builder.queryParam("parentOrigin", origin.toString());
    }

    private String validateRedirect(String redirect) {
        String value = redirect == null || redirect.isBlank() ? "/chat" : redirect.trim();
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new MateClawException("err.auth.yliyun.invalid_redirect",
                    400, "redirect 格式无效");
        }
        if (uri.isAbsolute() || uri.getHost() != null || !value.startsWith("/")
                || value.startsWith("//") || uri.getRawFragment() != null) {
            throw new MateClawException("err.auth.yliyun.invalid_redirect",
                    400, "redirect 不在允许范围内");
        }
        String path = uri.getPath();
        if (!"/chat".equals(path) && !"/embed/cloud-agent".equals(path)) {
            throw new MateClawException("err.auth.yliyun.invalid_redirect",
                    400, "redirect 不在允许范围内");
        }
        return path;
    }

    private String claimString(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null || String.valueOf(value).isBlank()
                || "null".equalsIgnoreCase(String.valueOf(value))) {
            throw new MateClawException("err.auth.yliyun.missing_" + key,
                    401, "ticket 中缺少 " + key);
        }
        return String.valueOf(value);
    }

    private String optionalClaimString(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        return value == null || String.valueOf(value).isBlank()
                ? null : String.valueOf(value).trim();
    }

    private Boolean optionalClaimBoolean(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String normalized = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return Boolean.FALSE;
        }
        throw new MateClawException("err.auth.yliyun.invalid_" + key,
                401, "ticket 中的 " + key + " 格式无效");
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
            return new LinkedHashMap<>(claims);
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
