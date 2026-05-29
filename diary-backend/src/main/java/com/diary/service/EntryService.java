package com.diary.service;

import com.diary.exception.BusinessException;
import com.diary.exception.VersionConflictException;
import com.diary.model.dto.CreateEntryRequest;
import com.diary.model.dto.EntryResponse;
import com.diary.model.dto.EntrySyncItem;
import com.diary.model.dto.UpdateEntryMetaRequest;
import com.diary.model.dto.UpdateEntryRequest;
import com.diary.model.entity.Entry;
import com.diary.repository.AttachmentRepository;
import com.diary.repository.EntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EntryService {

    private static final Logger log = LoggerFactory.getLogger(EntryService.class);

    private final EntryRepository entryRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;

    public EntryService(EntryRepository entryRepository, AttachmentRepository attachmentRepository,
                        AttachmentService attachmentService) {
        this.entryRepository = entryRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentService = attachmentService;
    }

    public List<EntrySyncItem> getSyncSummaries(String userId, Instant since) {
        List<Object[]> rows = since != null
                ? entryRepository.findSyncSummariesByUserIdAndUpdatedAtAfter(userId, since)
                : entryRepository.findSyncSummariesByUserId(userId);
        List<EntrySyncItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            Object dateObj = row[1];
            LocalDate diaryDate;
            if (dateObj instanceof LocalDate) {
                diaryDate = (LocalDate) dateObj;
            } else if (dateObj instanceof java.sql.Date) {
                diaryDate = ((java.sql.Date) dateObj).toLocalDate();
            } else {
                diaryDate = LocalDate.parse(dateObj.toString());
            }
            items.add(new EntrySyncItem(
                    (String) row[0],
                    diaryDate,
                    (Instant) row[2]
            ));
        }
        return items;
    }

    public List<EntryResponse> getBatch(String userId, List<String> ids) {
        List<Entry> entries = entryRepository.findByIdInAndUserId(ids, userId);
        return entries.stream().map(EntryResponse::from).toList();
    }

    @Transactional
    public EntryResponse create(String userId, CreateEntryRequest req) {
        Entry entry = new Entry(
                UUID.randomUUID().toString(),
                userId,
                req.getDiaryDate(),
                req.getMood(),
                req.getWeather(),
                req.isFavorite(),
                req.getEncryptedPayload(),
                req.getIv()
        );
        entry = entryRepository.save(entry);

        if (req.getAttachmentIds() != null && !req.getAttachmentIds().isEmpty()) {
            attachmentRepository.updateDiaryIdByIdIn(entry.getId(), req.getAttachmentIds(), userId);
        }

        return EntryResponse.from(entry);
    }

    @Transactional
    public EntryResponse update(String userId, String entryId, UpdateEntryRequest req) {
        Entry entry = entryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new BusinessException(404, "日记不存在"));

        Instant now = Instant.now();
        int updated = entryRepository.updateFull(
                entryId, userId, req.getVersion(),
                req.getDiaryDate(), req.getMood(), req.getWeather(), req.isFavorite(),
                req.getEncryptedPayload(), req.getIv(), now
        );

        if (updated == 0) {
            log.warn("Version conflict on entry update: entryId={}, clientVersion={}, serverVersion={}",
                    entryId, req.getVersion(), entry.getVersion());
            throw new VersionConflictException(entry.getVersion(), entry.getUpdatedAt());
        }

        if (req.getAttachmentIds() != null && !req.getAttachmentIds().isEmpty()) {
            attachmentRepository.updateDiaryIdByIdIn(entryId, req.getAttachmentIds(), userId);
        }

        entry = entryRepository.findById(entryId).orElseThrow();
        return EntryResponse.from(entry);
    }

    @Transactional
    public EntryResponse updateMeta(String userId, String entryId, UpdateEntryMetaRequest req) {
        Entry entry = entryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new BusinessException(404, "日记不存在"));

        Instant now = Instant.now();
        int updated = entryRepository.updateMeta(
                entryId, userId, req.getVersion(),
                req.getMood(), req.getWeather(), req.isFavorite(), req.getDiaryDate(), now
        );

        if (updated == 0) {
            log.warn("Version conflict on meta update: entryId={}, clientVersion={}, serverVersion={}",
                    entryId, req.getVersion(), entry.getVersion());
            throw new VersionConflictException(entry.getVersion(), entry.getUpdatedAt());
        }

        entry = entryRepository.findById(entryId).orElseThrow();
        return EntryResponse.from(entry);
    }

    @Transactional
    public void delete(String userId, String entryId) {
        if (!entryRepository.existsByIdAndUserId(entryId, userId)) {
            throw new BusinessException(404, "日记不存在");
        }
        attachmentService.deleteByDiaryId(entryId);
        entryRepository.deleteByIdAndUserId(entryId, userId);
        log.info("Entry deleted: entryId={}, userId={}", entryId, userId);
    }

    public long countByUserId(String userId) {
        return entryRepository.countByUserId(userId);
    }

    public long totalCount() {
        return entryRepository.count();
    }
}
