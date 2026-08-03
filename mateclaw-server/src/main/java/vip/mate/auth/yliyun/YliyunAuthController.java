package vip.mate.auth.yliyun;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
    private final YliyunTicketKeyRing keyRing;

    @Value("${mateclaw.auth.yliyun.ticket-issuer:yliyun}")
    private String ticketIssuer;

    @Value("${mateclaw.auth.yliyun.ticket-audience:mateclaw}")
    private String ticketAudience;

    @Value("${mateclaw.auth.yliyun.app-key:mateclaw_ai_assistant}")
    private String appKey;

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
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String nonce,
            @RequestParam(required = false) String hostHeader,
            jakarta.servlet.http.HttpServletRequest request) {

        // 1. 验证 ticket
        Map<String, Object> claims = verifyTicket(ticket);
        verifyIntegrationClaims(claims, parentOrigin, state, nonce);

        // 文件、会话和消息通道上下文以签名票据为准，阻止浏览器改写 query 越权切换文件。
        Map<String, Object> launchContext = claimMap(claims, "launchContext");
        fileId = contextString(launchContext, "fileId", fileId);
        fileName = contextString(launchContext, "fileName", fileName);
        folderId = contextString(launchContext, "folderId", folderId);
        folderName = contextString(launchContext, "folderName", folderName);
        conversationId = contextString(launchContext, "conversationId", conversationId);
        channelId = contextString(launchContext, "channelId", channelId);

        // 2. 提取声明
        String yliyunUserId = claimString(claims, "userId");
        String yliyunTenantId = claimString(claims, "tenantId");
        String nickname = optionalClaimString(claims, "nickname");
        String account = optionalClaimString(claims, "account");
        String tenantName = optionalClaimString(claims, "tenantName");
        Boolean tenantAdmin = optionalClaimBoolean(claims, "tenantAdmin");
        String verifiedAppKey = claimString(claims, "appKey");
        Integer configVersion = Math.toIntExact(numberClaim(claims, "configVersion"));

        if (yliyunUserId == null || yliyunUserId.isBlank()) {
            throw new MateClawException("err.auth.yliyun.missing_user_id", 400, "ticket 中缺少 userId");
        }

        log.info("[Yliyun] Ticket verified: userId={}, tenantId={}", yliyunUserId, yliyunTenantId);

        // 3. 防重放：检查 ticket jti 是否已被使用
        replayService.consume(claimString(claims, "jti"));

        // 4. 查找或创建用户
        UserEntity user = userMappingService.findOrCreateUser(
                yliyunUserId, yliyunTenantId, nickname, account, tenantName, tenantAdmin,
                verifiedAppKey, configVersion);

        // 5. 签发 MateClaw JWT
        String token = authService.generateYliyunToken(user, verifiedAppKey, configVersion);

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
        if ("1".equals(hostHeader)) redirectBuilder.queryParam("hostHeader", "1");
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

    private void verifyIntegrationClaims(Map<String, Object> claims, String parentOrigin,
                                         String state, String nonce) {
        requireClaimEquals(claims, "iss", ticketIssuer);
        if (!audienceMatches(claims.get("aud"), ticketAudience)) {
            throw invalidClaim("aud");
        }
        requireClaimEquals(claims, "appKey", appKey);
        requireClaimEquals(claims, "state", state);
        requireClaimEquals(claims, "nonce", nonce);
        requireClaimEquals(claims, "parentOrigin", parentOrigin);
        long configVersion = numberClaim(claims, "configVersion");
        long issuedAt = numberClaim(claims, "iat");
        long expiresAt = numberClaim(claims, "exp");
        long now = System.currentTimeMillis() / 1000;
        if (configVersion < 1 || issuedAt > now + 10 || expiresAt < now || expiresAt - issuedAt > 300) {
            throw new MateClawException("err.auth.yliyun.invalid_contract", 401,
                    "ticket 集成契约无效或已过期");
        }
    }

    private void requireClaimEquals(Map<String, Object> claims, String key, String expected) {
        String actual = optionalClaimString(claims, key);
        if (expected == null || expected.isBlank() || !Objects.equals(actual, expected)) {
            throw invalidClaim(key);
        }
    }

    private MateClawException invalidClaim(String key) {
        return new MateClawException("err.auth.yliyun.invalid_" + key, 401,
                "ticket 中的 " + key + " 无效");
    }

    private boolean audienceMatches(Object claim, String expected) {
        if (claim instanceof Collection<?> values) {
            return values.stream().anyMatch(value -> expected.equals(String.valueOf(value)));
        }
        return expected.equals(String.valueOf(claim));
    }

    private long numberClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            throw invalidClaim(key);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> claimMap(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String contextString(Map<String, Object> context, String key, String legacyValue) {
        Object value = context.get(key);
        if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        // 旧 query 只在没有新应用契约声明时兼容；新票据不允许绕过签名上下文。
        return context.isEmpty() ? legacyValue : null;
    }

    /**
     * 验证 ticket：支持 JWT 格式和简化 HMAC 格式。
     *
     * JWT 格式: header.payload.signature
     * HMAC 格式: base64url(payload).base64url(signature)
     */
    private Map<String, Object> verifyTicket(String ticket) {
        // 先尝试 JWT 格式
        for (YliyunTicketKeyRing.VerificationKey key : keyRing.verificationKeys(null)) {
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(key.secretKey())
                        .build()
                        .parseSignedClaims(ticket)
                        .getPayload();
                Map<String, Object> result = new LinkedHashMap<>(claims);
                requireMatchingKeyId(result, key.keyId());
                log.debug("[Yliyun] JWT ticket verified with keyId={}", key.keyId());
                return result;
            } catch (Exception e) {
                log.debug("[Yliyun] JWT ticket did not match keyId={}", key.keyId());
            }
        }

        // 尝试简化 HMAC 格式: payload.sig
        try {
            String[] parts = ticket.split("\\.", 2);
            if (parts.length != 2) {
                throw new MateClawException("err.auth.yliyun.invalid_ticket", 401, "ticket 格式无效");
            }

            // 验证 HMAC 签名
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            String verifiedKeyId = keyRing.verifyBase64Url(
                    new String(payloadBytes, StandardCharsets.UTF_8), parts[1], null);
            if (verifiedKeyId == null) {
                throw new MateClawException("err.auth.yliyun.invalid_ticket", 401, "ticket 签名无效");
            }

            // 解析 JSON payload
            String json = new String(payloadBytes, StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(json, Map.class);
            requireMatchingKeyId(map, verifiedKeyId);

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

    private void requireMatchingKeyId(Map<String, Object> claims, String verifiedKeyId) {
        Object keyId = claims.get("kid");
        if (keyId != null && !String.valueOf(keyId).isBlank()
                && !Objects.equals(String.valueOf(keyId), verifiedKeyId)) {
            throw new MateClawException("err.auth.yliyun.invalid_kid", 401,
                    "ticket 中的 kid 无效");
        }
    }
}
