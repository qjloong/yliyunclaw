package vip.mate.auth.yliyun;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.common.result.R;
import vip.mate.exception.MateClawException;

import java.time.Instant;
import java.util.Map;

/** Receives signed, replay-protected tenant application revocations from Yliyun. */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/yliyun")
@RequiredArgsConstructor
public class YliyunSessionRevocationController {

    private static final long MAX_CLOCK_SKEW_SECONDS = 60;

    private final YliyunUserMappingService mappingService;
    private final YliyunTicketReplayService replayService;
    private final YliyunTicketKeyRing keyRing;

    @Value("${mateclaw.auth.yliyun.app-key:mateclaw_ai_assistant}")
    private String expectedAppKey;

    @PostMapping("/revoke")
    public R<Map<String, Object>> revoke(
            @RequestHeader("X-Yliyun-Signature") String signature,
            @RequestHeader(value = "X-Yliyun-Key-Id", required = false) String keyId,
            @RequestBody YliyunSessionRevocationRequest request) {
        validate(request, signature, keyId, false);
        replayService.consume("revoke-" + request.getNonce());
        int updated = mappingService.revokeTenantEntitlement(
                request.getTenantId(), request.getAppKey(), request.getConfigVersion());
        log.info("[Yliyun Session Revoke] tenantId={}, appKey={}, configVersion={}, mappings={}",
                request.getTenantId(), request.getAppKey(), request.getConfigVersion(), updated);
        return R.ok(Map.of("revokedMappings", updated,
                "configVersion", request.getConfigVersion()));
    }

    /**
     * Disable the application-owned assistant while retaining the tenant
     * workspace, provider settings and conversation history for reversible
     * re-enable. A distinct signed payload prevents a valid revoke request
     * from being replayed as a deactivation request.
     */
    @PostMapping("/deactivate")
    public R<Map<String, Object>> deactivate(
            @RequestHeader("X-Yliyun-Signature") String signature,
            @RequestHeader(value = "X-Yliyun-Key-Id", required = false) String keyId,
            @RequestBody YliyunSessionRevocationRequest request) {
        validate(request, signature, keyId, true);
        replayService.consume("deactivate-" + request.getNonce());
        YliyunUserMappingService.TenantApplicationDeactivation result =
                mappingService.deactivateTenantApplication(
                        request.getTenantId(), request.getAppKey(), request.getConfigVersion());
        log.info("[Yliyun Application Deactivate] tenantId={}, appKey={}, configVersion={}, "
                        + "mappings={}, retainedWorkspaces={}, agents={}",
                request.getTenantId(), request.getAppKey(), request.getConfigVersion(),
                result.revokedMappings(), result.retainedWorkspaces(), result.deactivatedAgents());
        return R.ok(Map.of(
                "revokedMappings", result.revokedMappings(),
                "retainedWorkspaces", result.retainedWorkspaces(),
                "deactivatedAgents", result.deactivatedAgents(),
                "configVersion", request.getConfigVersion()));
    }

    private void validate(YliyunSessionRevocationRequest request, String signature,
                          String keyId, boolean deactivate) {
        if (request == null || request.getIssuedAt() == null || request.getConfigVersion() == null
                || request.getConfigVersion() < 1 || request.getAppKey() == null
                || request.getAppKey().isBlank() || request.getTenantId() == null
                || request.getTenantId().isBlank() || request.getNonce() == null
                || !request.getNonce().matches("[a-zA-Z0-9_-]{16,96}")) {
            throw unauthorized("撤销请求格式无效");
        }
        if (!expectedAppKey.equals(request.getAppKey().trim())) {
            throw unauthorized("撤销请求应用标识无效");
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - request.getIssuedAt()) > MAX_CLOCK_SKEW_SECONDS) {
            throw unauthorized("撤销请求已过期");
        }
        String verifiedKeyId = keyRing.verifyBase64Url(
                payload(request, deactivate), signature, keyId);
        if (verifiedKeyId == null) {
            throw unauthorized("撤销请求签名无效");
        }
        log.debug("[Yliyun Session Revoke] signature verified with keyId={}", verifiedKeyId);
    }

    private String payload(YliyunSessionRevocationRequest request, boolean deactivate) {
        String payload = String.join("\n", request.getAppKey().trim(), request.getTenantId().trim(),
                String.valueOf(request.getConfigVersion()), String.valueOf(request.getIssuedAt()),
                request.getNonce());
        return deactivate ? "DEACTIVATE\n" + payload : payload;
    }

    private MateClawException unauthorized(String message) {
        return new MateClawException("err.auth.yliyun.invalid_revoke", 401, message);
    }
}
