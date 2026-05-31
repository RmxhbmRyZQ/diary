package com.diary.service;

import com.diary.exception.BusinessException;
import com.diary.exception.VersionConflictException;
import com.diary.model.dto.*;
import com.diary.model.entity.Entry;
import com.diary.model.entity.User;
import com.diary.repository.AttachmentRepository;
import com.diary.repository.EntryRepository;
import com.diary.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class EntryServiceIntegrationTest {

    @Autowired
    private EntryService entryService;

    @Autowired
    private EntryRepository entryRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private UserRepository userRepository;

    private String userId;
    private String entryId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        User user = new User(userId, "entrytest", "auth-hash", "salt-auth",
                "encrypted-dek", "salt-enc", 1, "{}");
        userRepository.save(user);
    }

    private CreateEntryRequest createEntryRequest(LocalDate date, String mood, String weather) {
        CreateEntryRequest req = new CreateEntryRequest();
        req.setDiaryDate(date);
        req.setMood(mood);
        req.setWeather(weather);
        req.setFavorite(false);
        req.setEncryptedPayload("encrypted-payload-base64");
        req.setIv("iv-base64");
        return req;
    }

    private EntryResponse createEntry(LocalDate date, String mood, String weather) {
        return entryService.create(userId, createEntryRequest(date, mood, weather));
    }

    @Test
    void should_create_entry_and_persist_to_database() {
        EntryResponse resp = createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");

        Entry found = entryRepository.findByIdAndUserId(resp.getId(), userId).orElseThrow();
        assertThat(found.getDiaryDate()).isEqualTo(LocalDate.of(2026, 5, 27));
        assertThat(found.getMood()).isEqualTo("happy");
        assertThat(found.getWeather()).isEqualTo("sunny");
        assertThat(found.isFavorite()).isFalse();
        assertThat(found.getEncryptedPayload()).isEqualTo("encrypted-payload-base64");
        assertThat(found.getIv()).isEqualTo("iv-base64");
        assertThat(found.getVersion()).isEqualTo(1);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getUserId()).isEqualTo(userId);
    }

    @Test
    void should_get_sync_summaries_for_user() {
        createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");
        createEntry(LocalDate.of(2026, 5, 28), "sad", "rainy");

        List<EntrySyncItem> summaries = entryService.getSyncSummaries(userId, null);
        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).getId()).isNotNull();
        assertThat(summaries.get(0).getUpdatedAt()).isNotNull();
    }

    @Test
    void should_get_batch_of_entries_by_id() {
        EntryResponse e1 = createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");
        EntryResponse e2 = createEntry(LocalDate.of(2026, 5, 28), "sad", "rainy");
        createEntry(LocalDate.of(2026, 5, 29), "neutral", "cloudy");

        List<EntryResponse> batch = entryService.getBatch(userId, List.of(e1.getId(), e2.getId()));
        assertThat(batch).hasSize(2);
        assertThat(batch).extracting(EntryResponse::getId).containsExactlyInAnyOrder(e1.getId(), e2.getId());
    }

    @Test
    void should_return_empty_list_for_batch_when_ids_dont_match() {
        createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");

        List<EntryResponse> batch = entryService.getBatch(userId, List.of("nonexistent-id"));
        assertThat(batch).isEmpty();
    }

    @Test
    void should_not_return_other_users_entries_in_batch() {
        EntryResponse e1 = createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");

        String otherUserId = UUID.randomUUID().toString();
        User otherUser = new User(otherUserId, "other", "hash", "salt", "dek", "saltEnc", 1, "{}");
        userRepository.save(otherUser);

        List<EntryResponse> batch = entryService.getBatch(otherUserId, List.of(e1.getId()));
        assertThat(batch).isEmpty();
    }

    @Test
    void should_update_entry_full_and_increment_version() {
        EntryResponse created = createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");

        UpdateEntryRequest req = new UpdateEntryRequest();
        req.setDiaryDate(LocalDate.of(2026, 5, 28));
        req.setMood("excited");
        req.setWeather("windy");
        req.setFavorite(true);
        req.setEncryptedPayload("updated-payload");
        req.setIv("new-iv");
        req.setVersion(1);

        EntryResponse resp = entryService.update(userId, created.getId(), req);

        assertThat(resp.getVersion()).isEqualTo(2);
        assertThat(resp.getEncryptedPayload()).isEqualTo("updated-payload");
        assertThat(resp.getMood()).isEqualTo("excited");

        Entry found = entryRepository.findById(created.getId()).orElseThrow();
        assertThat(found.getVersion()).isEqualTo(2);
        assertThat(found.getEncryptedPayload()).isEqualTo("updated-payload");
        assertThat(found.getIv()).isEqualTo("new-iv");
        assertThat(found.getMood()).isEqualTo("excited");
        assertThat(found.getWeather()).isEqualTo("windy");
        assertThat(found.isFavorite()).isTrue();
    }

    @Test
    void should_throw_version_conflict_on_stale_update() {
        EntryResponse created = createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");

        UpdateEntryRequest req = new UpdateEntryRequest();
        req.setDiaryDate(LocalDate.of(2026, 5, 27));
        req.setEncryptedPayload("updated-payload");
        req.setIv("new-iv");
        req.setVersion(0);

        assertThatThrownBy(() -> entryService.update(userId, created.getId(), req))
                .isInstanceOf(VersionConflictException.class)
                .hasFieldOrPropertyWithValue("serverVersion", 1);

        Entry found = entryRepository.findById(created.getId()).orElseThrow();
        assertThat(found.getVersion()).isEqualTo(1);
        assertThat(found.getEncryptedPayload()).isEqualTo("encrypted-payload-base64");
    }

    @Test
    void should_update_meta_and_increment_version() {
        EntryResponse created = createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");

        UpdateEntryMetaRequest req = new UpdateEntryMetaRequest();
        req.setMood("sad");
        req.setWeather("rainy");
        req.setFavorite(true);
        req.setDiaryDate(LocalDate.of(2026, 5, 26));
        req.setVersion(1);

        EntryResponse resp = entryService.updateMeta(userId, created.getId(), req);

        assertThat(resp.getMood()).isEqualTo("sad");
        assertThat(resp.getWeather()).isEqualTo("rainy");
        assertThat(resp.isFavorite()).isTrue();
        assertThat(resp.getVersion()).isEqualTo(2);

        Entry found = entryRepository.findById(created.getId()).orElseThrow();
        assertThat(found.getVersion()).isEqualTo(2);
        assertThat(found.getMood()).isEqualTo("sad");
        assertThat(found.getWeather()).isEqualTo("rainy");
        assertThat(found.isFavorite()).isTrue();
        assertThat(found.getDiaryDate()).isEqualTo(LocalDate.of(2026, 5, 26));
        assertThat(found.getEncryptedPayload()).isEqualTo("encrypted-payload-base64");
    }

    @Test
    void should_throw_version_conflict_on_stale_meta_update() {
        EntryResponse created = createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");

        UpdateEntryMetaRequest req = new UpdateEntryMetaRequest();
        req.setMood("sad");
        req.setVersion(0);

        assertThatThrownBy(() -> entryService.updateMeta(userId, created.getId(), req))
                .isInstanceOf(VersionConflictException.class);

        Entry found = entryRepository.findById(created.getId()).orElseThrow();
        assertThat(found.getMood()).isEqualTo("happy");
    }

    @Test
    void should_delete_entry_and_cascade_attachments() {
        EntryResponse created = createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");

        com.diary.model.entity.Attachment att = new com.diary.model.entity.Attachment(
                UUID.randomUUID().toString(), created.getId(), userId,
                "/path/to/file", "att-iv", "image/png", "sha256hash");
        attachmentRepository.save(att);

        entryService.delete(userId, created.getId());

        assertThat(entryRepository.existsById(created.getId())).isFalse();
        assertThat(attachmentRepository.findByDiaryId(created.getId())).isEmpty();
    }

    @Test
    void should_delete_attachment_files_when_deleting_entry() throws Exception {
        EntryResponse created = createEntry(LocalDate.of(2026, 5, 28), "happy", null);

        Path tempFile = Files.createTempFile("diary-test-att", ".tmp");
        Files.writeString(tempFile, "test content");

        com.diary.model.entity.Attachment att = new com.diary.model.entity.Attachment(
                UUID.randomUUID().toString(), created.getId(), userId,
                tempFile.toString(), "att-iv", "image/png", "sha256hash");
        attachmentRepository.save(att);

        assertThat(Files.exists(tempFile)).isTrue();

        entryService.delete(userId, created.getId());

        assertThat(Files.exists(tempFile)).isFalse();
        assertThat(entryRepository.existsById(created.getId())).isFalse();
        assertThat(attachmentRepository.findByDiaryId(created.getId())).isEmpty();
    }

    @Test
    void should_throw_not_found_when_deleting_nonexistent_entry() {
        assertThatThrownBy(() -> entryService.delete(userId, "nonexistent-id"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    @Test
    void should_count_entries_by_user_id() {
        createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");
        createEntry(LocalDate.of(2026, 5, 28), "sad", "rainy");
        createEntry(LocalDate.of(2026, 5, 29), "neutral", "cloudy");

        assertThat(entryService.countByUserId(userId)).isEqualTo(3);
    }

    @Test
    void should_total_count_across_all_users() {
        createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");

        String otherUserId = UUID.randomUUID().toString();
        User otherUser = new User(otherUserId, "other", "hash", "salt", "dek", "saltEnc", 1, "{}");
        userRepository.save(otherUser);

        Entry otherEntry = new Entry(UUID.randomUUID().toString(), otherUserId,
                LocalDate.of(2026, 5, 28), "sad", "rainy", false,
                "payload", "iv");
        entryRepository.save(otherEntry);

        assertThat(entryService.totalCount()).isEqualTo(2);
    }

    @Test
    void should_throw_not_found_when_updating_nonexistent_entry() {
        UpdateEntryRequest req = new UpdateEntryRequest();
        req.setDiaryDate(LocalDate.of(2026, 5, 27));
        req.setEncryptedPayload("payload");
        req.setIv("iv");
        req.setVersion(1);

        assertThatThrownBy(() -> entryService.update(userId, "nonexistent", req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    @Test
    void should_create_entry_and_bind_attachments() {
        com.diary.model.entity.Attachment att = new com.diary.model.entity.Attachment(
                UUID.randomUUID().toString(), "00000000-0000-0000-0000-000000000000", userId,
                "/path/to/file", "att-iv", "image/png", "sha256hash");
        attachmentRepository.save(att);

        CreateEntryRequest req = createEntryRequest(LocalDate.of(2026, 5, 28), "happy", "sunny");
        req.setAttachmentIds(List.of(att.getId()));

        EntryResponse resp = entryService.create(userId, req);

        Entry found = entryRepository.findByIdAndUserId(resp.getId(), userId).orElseThrow();
        assertThat(found.getDiaryDate()).isEqualTo(LocalDate.of(2026, 5, 28));

        com.diary.model.entity.Attachment boundAtt = attachmentRepository.findById(att.getId()).orElseThrow();
        assertThat(boundAtt.getDiaryId()).isEqualTo(resp.getId());
    }

    @Test
    void should_update_entry_and_bind_new_attachments() {
        EntryResponse created = createEntry(LocalDate.of(2026, 5, 27), "happy", "sunny");

        com.diary.model.entity.Attachment att = new com.diary.model.entity.Attachment(
                UUID.randomUUID().toString(), "00000000-0000-0000-0000-000000000000", userId,
                "/path/to/file", "att-iv", "image/png", "sha256hash");
        attachmentRepository.save(att);

        UpdateEntryRequest req = new UpdateEntryRequest();
        req.setDiaryDate(LocalDate.of(2026, 5, 28));
        req.setEncryptedPayload("updated-payload");
        req.setIv("new-iv");
        req.setVersion(1);
        req.setAttachmentIds(List.of(att.getId()));

        entryService.update(userId, created.getId(), req);

        com.diary.model.entity.Attachment boundAtt = attachmentRepository.findById(att.getId()).orElseThrow();
        assertThat(boundAtt.getDiaryId()).isEqualTo(created.getId());
    }
}
