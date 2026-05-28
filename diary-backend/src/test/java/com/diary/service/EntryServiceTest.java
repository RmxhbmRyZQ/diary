package com.diary.service;

import com.diary.exception.BusinessException;
import com.diary.exception.VersionConflictException;
import com.diary.model.dto.CreateEntryRequest;
import com.diary.model.dto.EntryResponse;
import com.diary.model.dto.UpdateEntryMetaRequest;
import com.diary.model.dto.UpdateEntryRequest;
import com.diary.model.entity.Entry;
import com.diary.repository.AttachmentRepository;
import com.diary.repository.EntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntryServiceTest {

    @Mock
    private EntryRepository entryRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AttachmentService attachmentService;

    @InjectMocks
    private EntryService entryService;

    private String userId;
    private String entryId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        entryId = UUID.randomUUID().toString();
    }

    @Test
    void should_create_entry_successfully() {
        CreateEntryRequest req = new CreateEntryRequest();
        req.setDiaryDate(LocalDate.of(2026, 5, 27));
        req.setMood("happy");
        req.setWeather("sunny");
        req.setEncryptedPayload("encrypted-data");
        req.setIv("iv-base64");

        when(entryRepository.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));

        EntryResponse resp = entryService.create(userId, req);

        assertThat(resp.getDiaryDate()).isEqualTo(LocalDate.of(2026, 5, 27));
        assertThat(resp.getMood()).isEqualTo("happy");
        verify(entryRepository).save(any(Entry.class));
    }

    @Test
    void should_update_entry_with_valid_version() {
        UpdateEntryRequest req = new UpdateEntryRequest();
        req.setDiaryDate(LocalDate.of(2026, 5, 27));
        req.setEncryptedPayload("updated-payload");
        req.setIv("new-iv");
        req.setVersion(1);

        Entry entry = createEntry();
        when(entryRepository.findByIdAndUserId(entryId, userId)).thenReturn(Optional.of(entry));
        when(entryRepository.updateFull(anyString(), anyString(), anyInt(), any(), any(), any(),
                anyBoolean(), anyString(), anyString(), any(Instant.class))).thenReturn(1);
        when(entryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        EntryResponse resp = entryService.update(userId, entryId, req);

        assertThat(resp).isNotNull();
    }

    @Test
    void should_throw_version_conflict_when_version_mismatch() {
        UpdateEntryRequest req = new UpdateEntryRequest();
        req.setDiaryDate(LocalDate.of(2026, 5, 27));
        req.setEncryptedPayload("updated-payload");
        req.setIv("new-iv");
        req.setVersion(1);

        Entry entry = createEntry();
        entry.setVersion(3); // server version is ahead
        when(entryRepository.findByIdAndUserId(entryId, userId)).thenReturn(Optional.of(entry));
        when(entryRepository.updateFull(anyString(), anyString(), anyInt(), any(), any(), any(),
                anyBoolean(), anyString(), anyString(), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> entryService.update(userId, entryId, req))
                .isInstanceOf(VersionConflictException.class)
                .hasFieldOrPropertyWithValue("serverVersion", 3);
    }

    @Test
    void should_update_meta_with_valid_version() {
        UpdateEntryMetaRequest req = new UpdateEntryMetaRequest();
        req.setMood("sad");
        req.setWeather("rainy");
        req.setFavorite(true);
        req.setDiaryDate(LocalDate.of(2026, 5, 26));
        req.setVersion(1);

        Entry entry = createEntry();
        when(entryRepository.findByIdAndUserId(entryId, userId)).thenReturn(Optional.of(entry));
        when(entryRepository.updateMeta(anyString(), anyString(), anyInt(), anyString(),
                anyString(), anyBoolean(), any(), any(Instant.class))).thenReturn(1);
        when(entryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        EntryResponse resp = entryService.updateMeta(userId, entryId, req);

        assertThat(resp).isNotNull();
    }

    @Test
    void should_throw_not_found_when_entry_does_not_exist() {
        when(entryRepository.existsByIdAndUserId(entryId, userId)).thenReturn(false);

        assertThatThrownBy(() -> entryService.delete(userId, entryId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    @Test
    void should_delete_entry_and_attachments() {
        when(entryRepository.existsByIdAndUserId(entryId, userId)).thenReturn(true);

        entryService.delete(userId, entryId);

        verify(attachmentService).deleteByDiaryId(entryId);
        verify(entryRepository).deleteByIdAndUserId(entryId, userId);
    }

    private Entry createEntry() {
        Entry entry = new Entry(
                entryId, userId,
                LocalDate.of(2026, 5, 27),
                "happy", "sunny", false,
                "encrypted-payload", "iv-base64"
        );
        entry.setId(entryId);
        return entry;
    }
}
