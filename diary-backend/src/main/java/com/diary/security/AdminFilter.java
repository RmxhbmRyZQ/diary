package com.diary.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(2)
public class AdminFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AdminFilter.class);
    private static final int SESSION_TTL_MINUTES = 30;

    private static class AdminSession {
        final String adminId;
        final Instant createdAt;

        AdminSession(String adminId) {
            this.adminId = adminId;
            this.createdAt = Instant.now();
        }

        boolean isExpired() {
            return createdAt.plus(SESSION_TTL_MINUTES, ChronoUnit.MINUTES).isBefore(Instant.now());
        }
    }

    private final Map<String, AdminSession> adminSessions = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();

        if (!path.startsWith("/admin")) {
            chain.doFilter(request, response);
            return;
        }

        if (path.equals("/admin/login")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            res.setStatus(401);
            res.setContentType("application/json; charset=UTF-8");
            res.getWriter().write("{\"code\":401,\"message\":\"管理员未认证\",\"data\":null}");
            return;
        }

        String token = authHeader.substring(7);
        AdminSession session = adminSessions.get(token);
        if (session == null || session.isExpired()) {
            if (session != null) {
                adminSessions.remove(token);
            }
            res.setStatus(401);
            res.setContentType("application/json; charset=UTF-8");
            res.getWriter().write("{\"code\":401,\"message\":\"管理员 Session 无效或已过期\",\"data\":null}");
            return;
        }

        req.setAttribute("adminId", session.adminId);
        chain.doFilter(request, response);
    }

    @Scheduled(fixedRate = 300000)
    public void evictExpiredSessions() {
        adminSessions.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    public void registerSession(String token, String adminId) {
        adminSessions.put(token, new AdminSession(adminId));
    }

    public void removeSession(String token) {
        adminSessions.remove(token);
    }
}
