package com.aives.repository;

import com.aives.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {
    List<Question> findBySubjectUuid(UUID subjectUuid);
    List<Question> findBySubjectCode(String subjectCode);
    List<Question> findByDifficulty(String difficulty);
}
