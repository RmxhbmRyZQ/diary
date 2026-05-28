package com.diary.repository;

import com.diary.model.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {

    List<Session> findByUserId(String userId);

    void deleteAllByUserId(String userId);

    void deleteByExpiresAtBefore(Instant threshold);
}
