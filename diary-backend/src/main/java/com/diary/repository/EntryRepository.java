package com.diary.repository;

import com.diary.model.entity.Entry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EntryRepository extends JpaRepository<Entry, String> {

    List<Entry> findByUserId(String userId);

    List<Entry> findByIdInAndUserId(List<String> ids, String userId);

    Optional<Entry> findByIdAndUserId(String id, String userId);

    boolean existsByIdAndUserId(String id, String userId);

    @Query("SELECT e.userId, COUNT(e) FROM Entry e GROUP BY e.userId")
    List<Object[]> countByUserIds();

    long countByUserId(String userId);

    long count();

    @Query("SELECT e.id, e.diaryDate, e.updatedAt FROM Entry e WHERE e.userId = :userId")
    List<Object[]> findSyncSummariesByUserId(@Param("userId") String userId);

    @Query("SELECT e.id, e.diaryDate, e.updatedAt FROM Entry e WHERE e.userId = :userId AND e.updatedAt > :since")
    List<Object[]> findSyncSummariesByUserIdAndUpdatedAtAfter(@Param("userId") String userId, @Param("since") Instant since);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Entry e SET e.mood = :mood, e.weather = :weather, e.favorite = :favorite, " +
           "e.diaryDate = :diaryDate, e.version = e.version + 1, e.updatedAt = :now " +
           "WHERE e.id = :id AND e.userId = :userId AND e.version = :version")
    int updateMeta(@Param("id") String id, @Param("userId") String userId,
                   @Param("version") int version,
                   @Param("mood") String mood, @Param("weather") String weather,
                   @Param("favorite") boolean favorite, @Param("diaryDate") LocalDate diaryDate,
                   @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Entry e SET e.diaryDate = :diaryDate, e.mood = :mood, e.weather = :weather, " +
           "e.favorite = :favorite, e.encryptedPayload = :encryptedPayload, e.iv = :iv, " +
           "e.version = e.version + 1, e.updatedAt = :now " +
           "WHERE e.id = :id AND e.userId = :userId AND e.version = :version")
    int updateFull(@Param("id") String id, @Param("userId") String userId,
                   @Param("version") int version,
                   @Param("diaryDate") LocalDate diaryDate, @Param("mood") String mood,
                   @Param("weather") String weather, @Param("favorite") boolean favorite,
                   @Param("encryptedPayload") String encryptedPayload, @Param("iv") String iv,
                   @Param("now") Instant now);

    void deleteByIdAndUserId(String id, String userId);

    @Modifying
    @Query("DELETE FROM Entry e WHERE e.userId = :userId")
    void deleteAllByUserId(@Param("userId") String userId);
}
