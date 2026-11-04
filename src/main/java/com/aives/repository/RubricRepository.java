package com.aives.repository;

import com.aives.entity.Rubric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RubricRepository extends JpaRepository<Rubric, UUID> {
    /**
     * Lấy TẤT CẢ rubric thuộc một môn học (gồm cả rubric template và rubric gắn vào câu hỏi).
     * Dùng cho mục đích thống kê / hiển thị đầy đủ.
     */
    List<Rubric> findBySubjectUuid(UUID subjectUuid);

    /**
     * Lấy rubric của một câu hỏi cụ thể (question_uuid khác NULL).
     */
    List<Rubric> findByQuestionUuid(UUID questionUuid);

    /**
     * Lấy RUBRIC TEMPLATE của môn học (question_uuid IS NULL) - dùng làm Barem chuẩn.
     */
    List<Rubric> findBySubjectUuidAndQuestionUuidIsNull(UUID subjectUuid);

    /**
     * Lấy rubric của các câu hỏi thuộc môn học (question_uuid IS NOT NULL).
     */
    List<Rubric> findBySubjectUuidAndQuestionUuidIsNotNull(UUID subjectUuid);

    void deleteByQuestionUuid(UUID questionUuid);
}
