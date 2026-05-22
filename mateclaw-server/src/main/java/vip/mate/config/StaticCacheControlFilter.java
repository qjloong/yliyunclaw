package vip.mate.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Keep the SPA entry files fresh while allowing Vite hashed assets to be cached by clients/proxies.
 */
@Component
public class StaticCacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod()))
                && isSpaEntryOrManifest(request.getRequestURI())) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSpaEntryOrManifest(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return true;
        }
        if (path.startsWith("/api/") || path.startsWith("/assets/") || path.startsWith("/downloads/")
                || path.startsWith("/icons/") || path.startsWith("/logo/")) {
            return false;
        }
        if (path.endsWith("/index.html") || "/index.html".equals(path) || "/release-manifest.json".equals(path)) {
            return true;
        }
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        return !lastSegment.contains(".");
    }
}
