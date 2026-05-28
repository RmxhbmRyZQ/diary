package com.diary.controller;

import com.diary.config.AppConfig;
import com.diary.model.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ConfigController {

    private final AppConfig appConfig;

    public ConfigController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @GetMapping("/api/v1/config")
    public ResponseEntity<ApiResponse<?>> getConfig() {
        Map<String, Object> kdf = new LinkedHashMap<>();
        kdf.put("algorithm", appConfig.getKdf().getDefaultAlgorithm());
        kdf.put("iterations", appConfig.getKdf().getDefaultIterations());

        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("max_attachment_size_mb", appConfig.getUpload().getMaxFileSizeMb());
        limits.put("max_attachments_per_entry", appConfig.getUpload().getMaxPerEntry());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kdf", kdf);
        data.put("limits", limits);

        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
