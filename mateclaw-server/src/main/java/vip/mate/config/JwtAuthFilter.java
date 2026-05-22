package vip.mate.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * 支持两种 Token 传递方式：
 * 1. Authorization: Bearer <token>  （标准方式）
 * 2. ?token=<token>                 （SSE/EventSource 不支持自定义 Header，通过 query param 传递）
 *
 * @author MateClaw Team
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            try {
                Claims claims = authService.parseClaims(token);
                if (claims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    String username = claims.getSubject();
                    UserEntity user = authService.findByUsername(username);
                    if (user != null && Boolean.TRUE.equals(user.getEnabled())) {
                        var auth = new UsernamePasswordAuthenticationToken(
                                username, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase()))
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);

                        // 滑动窗口续期：Token 接近过期时自动签发新 Token
                        if (authService.isNearExpiry(claims)) {
                            String newToken = authService.renewToken(username);
                            if (newToken != null) {
                                response.setHeader("X-New-Token", newToken);
                                response.setHeader("Access-Control-Expose-Headers", "X-New-Token");
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Token 解析失败，继续匿名访问
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取 Token
     * 优先从 Authorization Header 读取，其次从 query param 读取（用于 SSE）
     */
    private String extractToken(HttpServletRequest request) {
        // 1. Authorization Header
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // 2. Query parameter（SSE 专用）
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        return null;
    }
}
