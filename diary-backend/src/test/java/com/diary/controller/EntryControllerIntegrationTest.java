package com.diary.controller;

import com.diary.model.entity.Attachment;
import com.diary.model.entity.Entry;
import com.diary.model.entity.User;
import com.diary.repository.AttachmentRepository;
import com.diary.repository.EntryRepository;
import com.diary.repository.UserRepository;
import com.diary.security.RateLimitFilter;
import com.diary.security.SessionFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class EntryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntryRepository entryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionFilter sessionFilter;

    @MockBean
    private RateLimitFilter rateLimitFilter;

    private String userId;
    private String entryId;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID().toString();
        User user = new User(userId, "entryctltest", "auth-hash", "salt-auth",
                "encrypted-dek", "encrypted-dek-recovery", "salt-enc", 1, "{}");
        userRepository.save(user);

        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(sessionFilter).doFilter(any(), any(), any());

        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(rateLimitFilter).doFilter(any(), any(), any());
    }

    private Entry saveEntryDirectly(String id, int version, String payload) {
        Entry entry = new Entry(id, userId, LocalDate.of(2026, 5, 27),
                "happy", "sunny", false, payload, "iv-base64");
        entry.setVersion(version);
        return entryRepository.save(entry);
    }

    @Test
    void should_create_entry_and_return_200_with_database_verification() throws Exception {
        Map<String, Object> body = Map.of(
                "diaryDate", "2026-05-27",
                "mood", "happy",
                "weather", "sunny",
                "favorite", false,
                "encryptedPayload", "encrypted-payload-base64",
                "iv", "iv-base64"
        );

        String respJson = mockMvc.perform(post("/api/v1/entries")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.diaryDate").value("2026-05-27"))
                .andExpect(jsonPath("$.data.mood").value("happy"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> respMap = objectMapper.readValue(respJson, Map.class);
        String id = (String) ((Map<String, Object>) respMap.get("data")).get("id");

        Entry found = entryRepository.findByIdAndUserId(id, userId).orElseThrow();
        assertThat(found.getDiaryDate()).isEqualTo(LocalDate.of(2026, 5, 27));
        assertThat(found.getMood()).isEqualTo("happy");
        assertThat(found.getEncryptedPayload()).isEqualTo("encrypted-payload-base64");
        assertThat(found.getVersion()).isEqualTo(1);
    }

    @Test
    void should_create_entry_and_bind_attachments_in_transaction() throws Exception {
        Attachment att1 = new Attachment(UUID.randomUUID().toString(), "placeholder",
                userId, "/tmp/test1", "iv1", "application/octet-stream", "sha256-a");
        Attachment att2 = new Attachment(UUID.randomUUID().toString(), "placeholder",
                userId, "/tmp/test2", "iv2", "image/png", "sha256-b");
        attachmentRepository.save(att1);
        attachmentRepository.save(att2);

        Map<String, Object> body = Map.of(
                "diaryDate", "2026-05-27",
                "mood", "happy",
                "weather", "sunny",
                "favorite", false,
                "encryptedPayload", "encrypted-payload-base64",
                "iv", "iv-base64",
                "attachmentIds", List.of(att1.getId(), att2.getId())
        );

        mockMvc.perform(post("/api/v1/entries")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Attachment bound1 = attachmentRepository.findById(att1.getId()).orElseThrow();
        Attachment bound2 = attachmentRepository.findById(att2.getId()).orElseThrow();
        assertThat(bound1.getDiaryId()).isNotEqualTo("placeholder");
        assertThat(bound2.getDiaryId()).isNotEqualTo("placeholder");
        assertThat(bound1.getDiaryId()).isEqualTo(bound2.getDiaryId());
    }

    @Test
    void should_return_400_when_missing_required_fields() throws Exception {
        Map<String, String> body = Map.of("diaryDate", "2026-05-27");

        mockMvc.perform(post("/api/v1/entries")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("userId", userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void should_sync_entries_and_return_200() throws Exception {
        saveEntryDirectly(UUID.randomUUID().toString(), 1, "payload-1");
        saveEntryDirectly(UUID.randomUUID().toString(), 1, "payload-2");

        mockMvc.perform(get("/api/v1/entries/sync")
                        .param("clientTime", Instant.now().toString())
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.entries").isArray())
                .andExpect(jsonPath("$.data.entries.length()").value(2));
    }

    @Test
    void should_get_batch_and_return_200() throws Exception {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        saveEntryDirectly(id1, 1, "payload-1");
        saveEntryDirectly(id2, 1, "payload-2");

        mockMvc.perform(get("/api/v1/entries/batch")
                        .param("ids", id1 + "," + id2)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.entries.length()").value(2));
    }

    @Test
    void should_update_entry_and_verify_database() throws Exception {
        entryId = UUID.randomUUID().toString();
        saveEntryDirectly(entryId, 1, "original-payload");

        Map<String, Object> body = Map.of(
                "diaryDate", "2026-05-28",
                "mood", "excited",
                "weather", "windy",
                "favorite", true,
                "encryptedPayload", "updated-payload",
                "iv", "new-iv",
                "version", 1
        );

        mockMvc.perform(put("/api/v1/entries/" + entryId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.encryptedPayload").value("updated-payload"))
                .andExpect(jsonPath("$.data.mood").value("excited"));

        Entry found = entryRepository.findById(entryId).orElseThrow();
        assertThat(found.getVersion()).isEqualTo(2);
        assertThat(found.getEncryptedPayload()).isEqualTo("updated-payload");
        assertThat(found.getMood()).isEqualTo("excited");
        assertThat(found.getDiaryDate()).isEqualTo(LocalDate.of(2026, 5, 28));
    }

    @Test
    void should_return_409_on_version_conflict() throws Exception {
        entryId = UUID.randomUUID().toString();
        saveEntryDirectly(entryId, 2, "original-payload");

        Map<String, Object> body = Map.of(
                "diaryDate", "2026-05-27",
                "encryptedPayload", "updated-payload",
                "iv", "new-iv",
                "version", 1
        );

        mockMvc.perform(put("/api/v1/entries/" + entryId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("userId", userId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.data.version").value(2));

        Entry found = entryRepository.findById(entryId).orElseThrow();
        assertThat(found.getEncryptedPayload()).isEqualTo("original-payload");
        assertThat(found.getVersion()).isEqualTo(2);
    }

    @Test
    void should_update_meta_and_verify_database() throws Exception {
        entryId = UUID.randomUUID().toString();
        saveEntryDirectly(entryId, 1, "original-payload");

        Map<String, Object> body = Map.of(
                "mood", "sad",
                "weather", "rainy",
                "favorite", true,
                "diaryDate", "2026-05-26",
                "version", 1
        );

        mockMvc.perform(patch("/api/v1/entries/" + entryId + "/meta")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mood").value("sad"))
                .andExpect(jsonPath("$.data.weather").value("rainy"))
                .andExpect(jsonPath("$.data.version").value(2));

        Entry found = entryRepository.findById(entryId).orElseThrow();
        assertThat(found.getVersion()).isEqualTo(2);
        assertThat(found.getMood()).isEqualTo("sad");
        assertThat(found.getWeather()).isEqualTo("rainy");
        assertThat(found.isFavorite()).isTrue();
        assertThat(found.getEncryptedPayload()).isEqualTo("original-payload");
    }

    @Test
    void should_delete_entry_and_verify_database() throws Exception {
        entryId = UUID.randomUUID().toString();
        saveEntryDirectly(entryId, 1, "payload");

        mockMvc.perform(delete("/api/v1/entries/" + entryId)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(entryRepository.existsById(entryId)).isFalse();
    }

    @Test
    void should_not_delete_entry_of_another_user() throws Exception {
        entryId = UUID.randomUUID().toString();
        saveEntryDirectly(entryId, 1, "payload");

        String otherUserId = UUID.randomUUID().toString();
        User otherUser = new User(otherUserId, "other", "hash", "salt", "dek", "dekr", "saltEnc", 1, "{}");
        userRepository.save(otherUser);

        mockMvc.perform(delete("/api/v1/entries/" + entryId)
                        .requestAttr("userId", otherUserId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        assertThat(entryRepository.existsById(entryId)).isTrue();
    }
}
