package vip.mate.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vip.mate.auth.model.LoginRequest;
import vip.mate.auth.model.LoginResponse;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.auth.yliyun.YliyunUserMappingService;
import vip.mate.common.result.R;
import vip.mate.exception.MateClawException;
import vip.mate.workspace.core.annotation.RequireGlobalAdmin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证接口
 *
 * @author MateClaw Team
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final YliyunUserMappingService yliyunUserMappingService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @Operation(summary = "获取当前登录会话")
    @GetMapping("/session")
    public R<Map<String, Object>> session(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new MateClawException("err.auth.unauthorized", 401, "未登录");
        }
        UserEntity user = authService.findByUsername(auth.getName());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new MateClawException("err.auth.user_not_found", 401, "用户不存在或已禁用");
        }
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", user.getId());
        session.put("username", user.getUsername());
        session.put("nickname", user.getNickname());
        session.put("displayName", user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname());
        session.put("role", user.getRole());
        yliyunUserMappingService.findSessionIdentity(user.getId()).ifPresent(identity -> {
            session.put("authSource", "yliyun");
            session.put("cloudUserId", identity.cloudUserId());
            session.put("cloudTenantId", identity.cloudTenantId());
            session.put("tenantName", identity.tenantName());
        });
        return R.ok(session);
    }

    @Operation(summary = "获取用户列表")
    @GetMapping("/users")
    @RequireGlobalAdmin
    public R<List<UserEntity>> listUsers() {
        return R.ok(authService.listUsers());
    }

    @Operation(summary = "创建用户")
    @PostMapping("/users")
    @RequireGlobalAdmin
    public R<UserEntity> createUser(@RequestBody UserEntity user) {
        return R.ok(authService.createUser(user));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/users/{id}/password")
    public R<Void> changePassword(
            @PathVariable Long id,
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            Authentication auth) {
        // Resolve user from the JWT principal — the {id} path segment is
        // informational. A user may only change their own password.
        UserEntity me = authService.findByUsername(auth.getName());
        if (me == null) {
            throw new MateClawException("err.auth.user_not_found", "用户不存在");
        }
        authService.changePassword(me.getId(), oldPassword, newPassword);
        return R.ok();
    }
}
