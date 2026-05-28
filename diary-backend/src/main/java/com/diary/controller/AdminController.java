package com.diary.controller;

import com.diary.model.dto.*;
import com.diary.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody AdminLoginRequest req) {
        Map<String, Object> result = adminService.login(req.getUsername(), req.getPassword());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            adminService.logout(authHeader.substring(7));
        }
        return ResponseEntity.ok(ApiResponse.success("已登出", null));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<?>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success(adminService.listUsers()));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<?>> deleteUser(@PathVariable String id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("用户已删除", null));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<?>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDashboard()));
    }

    @PutMapping("/config/kdf")
    public ResponseEntity<ApiResponse<?>> updateKdf(@Valid @RequestBody UpdateKdfRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.updateKdfConfig(req.getAlgorithm(), req.getIterations())));
    }

    @PutMapping("/config/rate-limit")
    public ResponseEntity<ApiResponse<?>> updateRateLimit(@Valid @RequestBody UpdateRateLimitRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.updateRateLimit(req.getEndpoint(), req.getLimit())));
    }

    @PutMapping("/config/attachments")
    public ResponseEntity<ApiResponse<?>> updateAttachmentLimits(
            @Valid @RequestBody UpdateAttachmentLimitsRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.updateAttachmentLimits(req.getMaxFileSizeMb(), req.getMaxPerEntry())));
    }
}
