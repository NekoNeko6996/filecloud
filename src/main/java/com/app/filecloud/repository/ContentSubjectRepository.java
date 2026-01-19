package com.app.filecloud.repository;

import com.app.filecloud.entity.ContentSubject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentSubjectRepository extends JpaRepository<ContentSubject, Integer> {
    // Có thể thêm tìm kiếm theo tên sau này
    Optional<ContentSubject> findByMainName(String mainName);

    @Query("SELECT s, " +
            "(SELECT COUNT(f.id) FROM FileNode f WHERE f.subjectMappingId IN (SELECT m.id FROM SubjectFolderMapping m WHERE m.subject.id = s.id)), "
            +
            "(SELECT COALESCE(SUM(f.size), 0L) FROM FileNode f WHERE f.subjectMappingId IN (SELECT m.id FROM SubjectFolderMapping m WHERE m.subject.id = s.id)) "
            +
            "FROM ContentSubject s " +
            "WHERE (:keyword IS NULL OR LOWER(s.mainName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.aliasName1) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            +
            "AND (:platformId IS NULL OR EXISTS (SELECT l FROM SubjectSocialLink l WHERE l.subject.id = s.id AND l.platform.id = :platformId))")
    List<Object[]> searchSubjectsWithStats(@Param("keyword") String keyword, @Param("platformId") Integer platformId);

    @Query("SELECT s FROM ContentSubject s WHERE " +
            "LOWER(s.mainName) IN :names OR " +
            "LOWER(s.aliasName1) IN :names OR " +
            "LOWER(s.aliasName2) IN :names")
    Optional<ContentSubject> findFirstByAnyName(@Param("names") List<String> names);
}