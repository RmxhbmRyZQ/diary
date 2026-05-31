package com.diary.security;

import com.diary.config.AppConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于滑动窗口的限流服务。
 * 使用时间戳队列替代 Guava RateLimiter，避免低速率下 tryAcquire() 恒返回 false 的问题。
 */
@Component
public class RateLimiterService {

    private final AppConfig appConfig;

    // 单 IP 维度（登录、注册、恢复）
    private final Map<String, Deque<Long>> loginWindows = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> registerWindows = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> recoveryWindows = new ConcurrentHashMap<>();

    // 单用户维度（通用 API、附件上传）
    private final Map<String, Deque<Long>> apiWindows = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> attachmentWindows = new ConcurrentHashMap<>();

    public RateLimiterService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public boolean tryLogin(String ip) {
        return tryAcquire(loginWindows, ip,
                appConfig.getRateLimit().getLoginPerMinute(), 60_000);
    }

    public boolean tryRegister(String ip) {
        return tryAcquire(registerWindows, ip,
                appConfig.getRateLimit().getRegisterPerHour(), 3_600_000);
    }

    public boolean tryRecovery(String ip) {
        return tryAcquire(recoveryWindows, ip,
                appConfig.getRateLimit().getRecoveryPerMinute(), 60_000);
    }

    public boolean tryApi(String userId) {
        return tryAcquire(apiWindows, userId,
                appConfig.getRateLimit().getApiPerMinute(), 60_000);
    }

    public boolean tryAttachment(String userId) {
        return tryAcquire(attachmentWindows, userId,
                appConfig.getRateLimit().getAttachmentPerMinute(), 60_000);
    }

    private boolean tryAcquire(Map<String, Deque<Long>> store, String key,
                               int maxRequests, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> q = store.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < now - windowMs) {
                q.pollFirst();
            }
            if (q.size() >= maxRequests) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
