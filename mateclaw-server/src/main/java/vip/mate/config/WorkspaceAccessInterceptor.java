package vip.mate.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.workspace.core.annotation.RequireWorkspaceRole;
import vip.mate.workspace.core.service.WorkspaceService;

/**
 * Workspace 访问拦截器
 * <p>
 * 对标注了 {@link RequireWorkspaceRole} 的 Controller 方法，自动校验：
 * 1. 当前用户已认证
 * 2. 请求中有 X-Workspace-Id header（否则使用默认 workspace=1）
 * 3. 用户是该 workspace 的成员且角色 ≥ 注解要求的最低角色
 * <p>
 * 成员资格查询使用 Caffeine 缓存（60s TTL），避免每次请求查库。
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceAccessInterceptor implements HandlerInterceptor {

    private final WorkspaceService workspaceService;
    private final AuthService authService;

    /** 默认 workspace ID（未传 header 时使用） */
    private static final long DEFAULT_WORKSPACE_ID = 1L;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只拦截 Controller 方法
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 检查方法是否标注了 @RequireWorkspaceRole
        RequireWorkspaceRole annotation = handlerMethod.getMethodAnnotation(RequireWorkspaceRole.class);
        if (annotation == null) {
            return true;
        }

        // 获取当前认证用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            // 未认证的请求由 Spring Security 处理，这里不拦截
            return true;
        }

        String username = auth.getName();
        UserEntity user = authService.findByUsername(username);
        if (user == null) {
            sendForbidden(response, "User not found");
            return false;
        }

        // 系统管理员跳过 workspace 权限检查（全局 admin 角色）
        if ("admin".equalsIgnoreCase(user.getRole())) {
            return true;
        }

        // 解析 workspace ID
        long workspaceId = resolveWorkspaceId(request);

        // 检查成员资格 + 角色
        String minRole = annotation.value();
        if (!workspaceService.hasPermissionCached(workspaceId, user.getId(), minRole)) {
            log.warn("Workspace access denied: user={}, workspaceId={}, requiredRole={}", username, workspaceId, minRole);
            sendForbidden(response, "Workspace permission denied: requires " + minRole + " role");
            return false;
        }

        return true;
    }

    private long resolveWorkspaceId(HttpServletRequest request) {
        String header = request.getHeader("X-Workspace-Id");
        if (header != null && !header.isBlank()) {
            try {
                return Long.parseLong(header.trim());
            } catch (NumberFormatException e) {
                return DEFAULT_WORKSPACE_ID;
            }
        }
        return DEFAULT_WORKSPACE_ID;
    }

    private void sendForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"msg\":\"" + message + "\",\"data\":null}");
    }
}
