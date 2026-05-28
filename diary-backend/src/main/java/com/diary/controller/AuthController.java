package com.diary.controller;

import com.diary.model.dto.*;
import com.diary.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> register(@Valid @RequestBody RegisterRequest req,
                                                   HttpServletRequest request) {
        String clientIp = getClientIp(request);
        var user = authService.register(req, clientIp);
        return ResponseEntity.ok(ApiResponse.success("注册成功",
                Map.of("user_id", user.getId(), "created_at", user.getCreatedAt())));
    }

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest req,
                                                 HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(req, response);
        return ResponseEntity.ok(ApiResponse.success("登录成功", loginResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(HttpServletRequest request,
                                                  HttpServletResponse response) {
        String sessionId = (String) request.getAttribute("sessionId");
        authService.logout(sessionId, response);
        return ResponseEntity.ok(ApiResponse.success("已登出", null));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<?>> changePassword(HttpServletRequest request,
                                                          @Valid @RequestBody ChangePasswordRequest req) {
        String userId = (String) request.getAttribute("userId");
        authService.changePassword(userId, req);
        return ResponseEntity.ok(ApiResponse.success("密码已修改，请重新登录", null));
    }

    @GetMapping("/kdf-info")
    public ResponseEntity<ApiResponse<?>> getKdfInfo(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        KdfInfoResponse info = authService.getKdfInfo(userId);
        return ResponseEntity.ok(ApiResponse.success(info));
    }

    @PutMapping("/recovery")
    public ResponseEntity<ApiResponse<?>> setRecovery(HttpServletRequest request,
                                                       @Valid @RequestBody SetRecoveryRequest req) {
        String userId = (String) request.getAttribute("userId");
        authService.setRecovery(userId, req);
        return ResponseEntity.ok(ApiResponse.success("托管信息已设置", null));
    }

    @GetMapping("/recovery")
    public ResponseEntity<ApiResponse<?>> getRecoveryInfo(@RequestParam String username) {
        Object data = authService.getRecoveryInfo(username);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/recovery/reset")
    public ResponseEntity<ApiResponse<?>> recoveryReset(@Valid @RequestBody RecoveryResetRequest req) {
        authService.recoveryReset(req);
        return ResponseEntity.ok(ApiResponse.success("密码已重置，请使用新密码登录", null));
    }

    @DeleteMapping("/recovery")
    public ResponseEntity<ApiResponse<?>> deleteRecovery(HttpServletRequest request,
                                                         @RequestBody Map<String, String> body) {
        String userId = (String) request.getAttribute("userId");
        String authKey = body.get("authKey");
        if (authKey == null || authKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "authKey 不能为空"));
        }
        authService.deleteRecovery(userId, authKey);
        return ResponseEntity.ok(ApiResponse.success("托管信息已删除", null));
    }

    @DeleteMapping("/account")
    public ResponseEntity<ApiResponse<?>> deleteAccount(HttpServletRequest request,
                                                         @Valid @RequestBody DeleteAccountRequest req) {
        String userId = (String) request.getAttribute("userId");
        authService.deleteAccount(userId, req.getAuthKey());
        return ResponseEntity.ok(ApiResponse.success("账户已注销", null));
    }
}
