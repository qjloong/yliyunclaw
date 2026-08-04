package vip.mate.auth.yliyun;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.common.result.R;
import vip.mate.exception.MateClawException;

@Tag(name = "Yliyun 云盘资源引用")
@RestController
@RequestMapping("/api/v1/auth/yliyun/resource-refs")
@RequiredArgsConstructor
public class YliyunCloudResourceRefController {

    private final YliyunCloudResourceRefService resourceRefService;
    private final YliyunCloudResourceStatusService resourceStatusService;

    @PostMapping
    @Operation(summary = "为当前云盘用户签发不可伪造的资源引用")
    public R<YliyunCloudResourceRefService.IssuedResourceRef> issue(
            @RequestBody YliyunCloudResourceRefService.IssueRequest request,
            Authentication authentication) {
        Long userId = authentication != null && authentication.getDetails() instanceof Number number
                ? number.longValue() : null;
        if (userId == null) {
            throw new MateClawException("err.auth.unauthenticated", 401, "Not authenticated");
        }
        return R.ok(resourceStatusService.issueVerified(request, userId));
    }

    @PostMapping("/rebind")
    @Operation(summary = "重新绑定已验证的云盘资源引用")
    public R<YliyunCloudResourceRefService.IssuedResourceRef> rebind(
            @RequestBody YliyunCloudResourceRefService.RebindRequest request,
            Authentication authentication) {
        Long userId = authentication != null && authentication.getDetails() instanceof Number number
                ? number.longValue() : null;
        if (userId == null) {
            throw new MateClawException("err.auth.unauthenticated", 401, "Not authenticated");
        }
        return R.ok(resourceRefService.rebind(request, userId));
    }

    @PostMapping("/status")
    @Operation(summary = "校验云盘资源的最新版本、删除及权限状态")
    public R<YliyunCloudResourceStatusService.ResourceStatus> status(
            @RequestBody YliyunCloudResourceStatusService.StatusRequest request,
            Authentication authentication) {
        Long userId = authenticatedUserId(authentication);
        return R.ok(resourceStatusService.status(request, userId));
    }

    @PostMapping("/refresh")
    @Operation(summary = "将已验证的云盘资源引用刷新到最新版本")
    public R<YliyunCloudResourceRefService.IssuedResourceRef> refresh(
            @RequestBody YliyunCloudResourceStatusService.StatusRequest request,
            Authentication authentication) {
        Long userId = authenticatedUserId(authentication);
        return R.ok(resourceStatusService.refresh(request, userId));
    }

    private Long authenticatedUserId(Authentication authentication) {
        Long userId = authentication != null && authentication.getDetails() instanceof Number number
                ? number.longValue() : null;
        if (userId == null) {
            throw new MateClawException("err.auth.unauthenticated", 401, "Not authenticated");
        }
        return userId;
    }
}
