package com.diary.repository;

import com.diary.model.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, String> {

    List<Attachment> findByDiaryId(String diaryId);

    List<Attachment> findByUserId(String userId);

    long countByDiaryId(String diaryId);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.diaryId = :diaryId")
    void deleteAllByDiaryId(@Param("diaryId") String diaryId);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.userId = :userId")
    void deleteAllByUserId(@Param("userId") String userId);

    List<Attachment> findByDiaryIdAndCreatedAtBefore(String diaryId, Instant threshold);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Attachment a SET a.diaryId = :diaryId WHERE a.id IN :ids AND a.userId = :userId")
    int updateDiaryIdByIdIn(@Param("diaryId") String diaryId, @Param("ids") List<String> ids,
                            @Param("userId") String userId);
}
