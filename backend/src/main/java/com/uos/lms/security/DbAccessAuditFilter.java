package com.uos.lms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * DB 접근이 수반되는 API 요청에 대한 감사 로그를 남긴다.
 * 인증된 사용자의 요청만 기록하며, 정적 파일 프록시 등은 제외한다.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DbAccessAuditFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        filterChain.doFilter(request, response);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return;
            }

            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();
            long duration = System.currentTimeMillis() - startTime;
            String userId = String.valueOf(auth.getPrincipal());
            String role = auth.getAuthorities().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElse("UNKNOWN");
            String clientIp = resolveClientIp(request);

            log.info("[DB_AUDIT] user={} role={} ip={} method={} uri={} status={} duration={}ms",
                    userId, role, clientIp, method, uri, status, duration);
        } catch (Exception e) {
            // 감사 로그 실패가 요청 처리에 영향을 주지 않도록 한다
            log.warn("Audit logging failed", e);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/files/")
                || path.equals("/health")
                || path.equals("/");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
