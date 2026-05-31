package com.diary.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(3)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiterService rateLimiter;

    public RateLimitFilter(RateLimiterService rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();
        String clientIp = getClientIp(req);

        if (path.startsWith("/api/v1/auth/login") && "POST".equalsIgnoreCase(req.getMethod())) {
            if (!rateLimiter.tryLogin(clientIp)) {
                sendRateLimited(res, "登录请求过于频繁，请稍后再试");
                return;
            }
        }

        if (path.equals("/admin/login") && "POST".equalsIgnoreCase(req.getMethod())) {
            if (!rateLimiter.tryLogin(clientIp)) {
                sendRateLimited(res, "登录请求过于频繁，请稍后再试");
                return;
            }
        }

        if (path.startsWith("/api/v1/auth/recovery")) {
            if (!rateLimiter.tryRecovery(clientIp)) {
                sendRateLimited(res, "请求过于频繁，请稍后再试");
                return;
            }
        }

        if (path.startsWith("/api/v1/attachments") && "POST".equalsIgnoreCase(req.getMethod())) {
            String userId = (String) req.getAttribute("userId");
            if (userId != null && !rateLimiter.tryAttachment(userId)) {
                sendRateLimited(res, "附件上传过于频繁，请稍后再试");
                return;
            }
        }

        if (path.startsWith("/api/v1/") && !path.startsWith("/api/v1/auth/")
                && !path.startsWith("/api/v1/config")
                && !path.startsWith("/api/v1/entries")) {
            String userId = (String) req.getAttribute("userId");
            if (userId != null && !rateLimiter.tryApi(userId)) {
                sendRateLimited(res, "请求过于频繁，请稍后再试");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void sendRateLimited(HttpServletResponse res, String message) throws IOException {
        res.setStatus(429);
        res.setContentType("application/json; charset=UTF-8");
        res.setHeader("Retry-After", "60");
        res.getWriter().write("{\"code\":429,\"message\":\"" + message + "\",\"data\":null}");
    }

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
