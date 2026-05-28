package com.diary.security;

import com.diary.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RateLimiterTest {

    private RateLimiterService rateLimiter;
    private AppConfig appConfig;
    private AppConfig.RateLimit rateLimitConfig;

    @BeforeEach
    void setUp() {
        appConfig = new AppConfig();
        rateLimitConfig = new AppConfig.RateLimit();
        rateLimitConfig.setLoginPerMinute(10);
        rateLimitConfig.setRegisterPerHour(10);
        rateLimitConfig.setRecoveryPerMinute(10);
        rateLimitConfig.setApiPerMinute(100);
        rateLimitConfig.setAttachmentPerMinute(20);

        appConfig.setRateLimit(rateLimitConfig);
        rateLimiter = new RateLimiterService(appConfig);
    }

    @Test
    void should_allow_requests_within_limit() {
        boolean result = rateLimiter.tryLogin("192.168.1.1");
        assertThat(result).isTrue();
    }

    @Test
    void should_track_different_ips_independently() {
        assertThat(rateLimiter.tryLogin("192.168.1.1")).isTrue();
        assertThat(rateLimiter.tryLogin("192.168.1.2")).isTrue();
    }

    @Test
    void should_allow_general_api_requests() {
        boolean result = rateLimiter.tryApi("test-user-id");
        assertThat(result).isTrue();
    }

    @Test
    void should_allow_attachment_upload_within_limit() {
        boolean result = rateLimiter.tryAttachment("test-user-id");
        assertThat(result).isTrue();
    }

    @Test
    void should_allow_recovery_requests() {
        boolean result = rateLimiter.tryRecovery("192.168.1.1");
        assertThat(result).isTrue();
    }

    @Test
    void should_rate_limit_excessive_login() {
        String ip = "10.0.0.1";
        // Warm up all permits
        for (int i = 0; i < 20; i++) {
            rateLimiter.tryLogin(ip);
        }
        // After exhausting, should be limited
        boolean limited = !rateLimiter.tryLogin(ip);
        assertThat(limited).isTrue();
    }

    @Test
    void should_rate_limit_excessive_api_calls() {
        String userId = "busy-user";
        for (int i = 0; i < 200; i++) {
            rateLimiter.tryApi(userId);
        }
        boolean limited = !rateLimiter.tryApi(userId);
        assertThat(limited).isTrue();
    }
}
