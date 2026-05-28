package com.diary.controller;

import com.diary.model.dto.*;
import com.diary.service.EntryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/entries")
public class EntryController {

    private final EntryService entryService;

    public EntryController(EntryService entryService) {
        this.entryService = entryService;
    }

    @GetMapping("/sync")
    public ResponseEntity<ApiResponse<?>> sync(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        List<EntrySyncItem> entries = entryService.getSyncSummaries(userId);
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
