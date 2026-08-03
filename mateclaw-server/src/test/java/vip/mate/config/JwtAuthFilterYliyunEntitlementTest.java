package vip.mate.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.pat.PersonalAccessTokenService;
import vip.mate.auth.service.AuthService;
import vip.mate.auth.yliyun.YliyunAuthCookieService;
import vip.mate.auth.yliyun.YliyunUserMappingService;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class JwtAuthFilterYliyunEntitlementTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void staleCloudCookieIsClearedAndNotAuthenticated() throws Exception {
        AuthService authService = mock(AuthService.class);
        YliyunAuthCookieService cookieService = mock(YliyunAuthCookieService.class);
        YliyunUserMappingService mappingService = mock(YliyunUserMappingService.class);
        JwtAuthFilter filter = new JwtAuthFilter(authService,
                mock(PersonalAccessTokenService.class), cookieService, mappingService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        Claims claims = cloudClaims();
        UserEntity user = cloudUser();
        when(cookieService.read(request)).thenReturn("cloud-token");
        when(cookieService.clearHeaderValue()).thenReturn("mateclaw_session=; Max-Age=0; Path=/");
        when(authService.parseClaims("cloud-token")).thenReturn(claims);
        when(authService.findByUsername("yliyun_135_215")).thenReturn(user);
        when(mappingService.isCurrentEntitlement(88L, "mateclaw_ai_assistant", 9))
                .thenReturn(false);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(response.getHeaders("Set-Cookie").stream()
                .anyMatch(value -> value.contains("Max-Age=0")));
        verify(chain).doFilter(request, response);
    }

    private Claims cloudClaims() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("yliyun_135_215");
        when(claims.get("authSource", String.class)).thenReturn("yliyun");
        when(claims.get("appKey", String.class)).thenReturn("mateclaw_ai_assistant");
        when(claims.get("configVersion", Number.class)).thenReturn(9);
        return claims;
    }

    private UserEntity cloudUser() {
        UserEntity user = new UserEntity();
        user.setId(88L);
        user.setUsername("yliyun_135_215");
        user.setRole("user");
        user.setEnabled(true);
        return user;
    }
}
