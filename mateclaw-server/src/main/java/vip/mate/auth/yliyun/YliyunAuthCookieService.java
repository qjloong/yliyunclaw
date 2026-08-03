package vip.mate.auth.yliyun;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Central cookie policy for the embedded Yliyun login.
 */
@Component
public class YliyunAuthCookieService {

    @Value("${mateclaw.auth.yliyun.cookie.name:mateclaw_session}")
    private String cookieName;

    @Value("${mateclaw.auth.yliyun.cookie.max-age-seconds:86400}")
    private long maxAgeSeconds;

    @Value("${mateclaw.auth.yliyun.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${mateclaw.auth.yliyun.cookie.secure:false}")
    private boolean secure;

    @Value("${mateclaw.auth.yliyun.cookie.domain:}")
    private String domain;

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public String headerValue(String token) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(cookieName, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofSeconds(Math.max(1, maxAgeSeconds)));
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain.trim());
        }
        return builder.build().toString();
    }

    public String clearHeaderValue() {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(cookieName, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ZERO);
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain.trim());
        }
        return builder.build().toString();
    }
}
