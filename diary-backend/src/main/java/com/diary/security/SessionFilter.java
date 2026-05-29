package com.diary.security;

import com.diary.model.entity.Session;
import com.diary.repository.SessionRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Component
@Order(1)
public class SessionFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SessionFilter.class);

    private final SessionRepository sessionRepository;

    public SessionFilter(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();

        if (isPublicPath(path, req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/admin")) {
            chain.doFilter(request, response);
            return;
        }

        String sessionId = extractSessionId(req);
        if (sessionId == null || sessionId.isEmpty()) {
            res.setStatus(401);
            res.setContentType("application/json; charset=UTF-8");
            res.getWriter().write("{\"code\":401,\"message\":\"未认证\",\"data\":null}");
            return;
        }

        Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty() || sessionOpt.get().isExpired()) {
            sessionOpt.ifPresent(s -> sessionRepository.delete(s));
            res.setStatus(401);
            res.setContentType("application/json; charset=UTF-8");
            res.getWriter().write("{\"code\":401,\"message\":\"Session 已过期\",\"data\":null}");
            return;
        }

        req.setAttribute("userId", sessionOpt.get().getUserId());
        req.setAttribute("sessionId", sessionId);

        log.debug("Session validated: {}", hashSessionId(sessionId));
        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String path, String method) {
        return path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/config")
                || (path.startsWith("/api/v1/auth/recovery")
                    && ("GET".equalsIgnoreCase(method)
                        || "POST".equalsIgnoreCase(method)));
    }

    private String extractSessionId(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("session_id".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String hashSessionId(String sessionId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sessionId.getBytes());
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "???";
        }
    }
}
