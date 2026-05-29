package com.diary.controller;

import com.diary.model.dto.*;
import com.diary.service.EntryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/entries")
public class EntryController {

    private static final long MAX_TIME_SKEW_SECONDS = 300; // 5 minutes

    private final EntryService entryService;

    public EntryController(EntryService entryService) {
        this.entryService = entryService;
    }

    @GetMapping("/sync")
    public ResponseEntity<ApiResponse<?>> sync(@RequestParam(required = false) String since,
                                                @RequestParam String clientTime,
                                                HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        Instant sinceTime = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceTime = Instant.parse(since);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "since 参数格式无效，需为 ISO 8601 时间戳"));
            }
        }

        try {
            Instant clientInstant = Instant.parse(clientTime);
            Instant serverNow = Instant.now();
            long skew = Math.abs(serverNow.getEpochSecond() - clientInstant.getEpochSecond());
            if (skew > MAX_TIME_SKEW_SECONDS) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error(400, "客户端时间与服务器时间偏差过大（" + skew + " 秒），请校准系统时间后重试"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "clientTime 参数格式无效，需为 ISO 8601 时间戳"));
        }

        List<EntrySyncItem> entries = entryService.getSyncSummaries(userId, sinceTime);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("entries", entries)));
    }

    @GetMapping("/batch")
    public ResponseEntity<ApiResponse<?>> batch(@RequestParam String ids, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        List<String> idList = Arrays.asList(ids.split(","));
        List<EntryResponse> entries = entryService.getBatch(userId, idList);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("entries", entries)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody CreateEntryRequest req,
                                                  HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        EntryResponse entry = entryService.create(userId, req);
        return ResponseEntity.ok(ApiResponse.success(entry));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> update(@PathVariable String id,
                                                  @Valid @RequestBody UpdateEntryRequest req,
                                                  HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        EntryResponse entry = entryService.update(userId, id, req);
        return ResponseEntity.ok(ApiResponse.success(entry));
    }

    @PatchMapping("/{id}/meta")
    public ResponseEntity<ApiResponse<?>> updateMeta(@PathVariable String id,
                                                      @RequestBody UpdateEntryMetaRequest req,
                                                      HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        EntryResponse entry = entryService.updateMeta(userId, id, req);
        return ResponseEntity.ok(ApiResponse.success(entry));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable String id,
                                                  HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        entryService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success("已删除", null));
    }
}
