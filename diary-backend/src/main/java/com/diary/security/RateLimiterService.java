package com.diary.security;

import com.diary.config.AppConfig;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterService {

    private final AppConfig appConfig;

    private final Map<String, RateLimiter> ipLoginLimiters = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> ipRegisterLimiters = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> ipRecoveryLimiters = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> userApiLimiters = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> userAttachmentLimiters = new ConcurrentHashMap<>();

    public RateLimiterService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public boolean tryLogin(String ip) {
        return ipLoginLimiters.computeIfAbsent(ip,
                k -> RateLimiter.create(appConfig.getRateLimit().getLoginPerMinute() / 60.0))
                .tryAcquire();
    }

    public boolean tryRegister(String ip) {
        return ipRegisterLimiters.computeIfAbsent(ip,
                k -> RateLimiter.create(appConfig.getRateLimit().getRegisterPerHour() / 3600.0))
                .tryAcquire();
    }

    public boolean tryRecovery(String ip) {
        return ipRecoveryLimiters.computeIfAbsent(ip,
                k -> RateLimiter.create(appConfig.getRateLimit().getRecoveryPerMinute() / 60.0))
                .tryAcquire();
    }

    public boolean tryApi(String userId) {
        return userApiLimiters.computeIfAbsent(userId,
                k -> RateLimiter.create(appConfig.getRateLimit().getApiPerMinute() / 60.0))
                .tryAcquire();
    }

    public boolean tryAttachment(String userId) {
        return userAttachmentLimiters.computeIfAbsent(userId,
                k -> RateLimiter.create(appConfig.getRateLimit().getAttachmentPerMinute() / 60.0))
                .tryAcquire();
    }
}
