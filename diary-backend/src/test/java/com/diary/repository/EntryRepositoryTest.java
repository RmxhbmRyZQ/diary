package com.diary.repository;

import com.diary.model.entity.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class EntryRepositoryTest {

    @Autowired
    private EntryRepository entryRepository;

    private String userId;
    private String entryId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        entryId = UUID.randomUUID().toString();

        Entry entry = new Entry(
                entryId, userId,
                LocalDate.of(2026, 5, 27),
                "happy", "sunny", false,
                "encrypted-payload-base64", "iv-base64"
        );
        entry.setId(entryId);
        entry.setCreatedAt(Instant.now());
        entry.setUpdatedAt(Instant.now());
        entryRepository.save(entry);
    }

    @Test
    void should_find_entry_by_id_and_user_id() {
        Optional<Entry> found = entryRepository.findByIdAndUserId(entryId, userId);
        assertThat(found).isPresent();
        assertThat(found.get().getDiaryDate()).isEqualTo(LocalDate.of(2026, 5, 27));
    }

    @Test
    void should_return_empty_for_wrong_user() {
        Optional<Entry> found = entryRepository.findByIdAndUserId(entryId, "wrong-user");
        assertThat(found).isEmpty();
    }

    @Test
    void should_find_entries_by_user_id() {
        List<Entry> entries = entryRepository.findByUserId(userId);
        assertThat(entries).hasSize(1);
    }

    @Test
    void should_count_by_user_id() {
        long count = entryRepository.countByUserId(userId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void should_delete_by_id_and_user_id() {
        entryRepository.deleteByIdAndUserId(entryId, userId);
        assertThat(entryRepository.existsById(entryId)).isFalse();
    }
}
