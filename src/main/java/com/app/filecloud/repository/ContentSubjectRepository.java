package com.app.filecloud.repository;

import com.app.filecloud.entity.ContentSubject;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentSubjectRepository extends JpaRepository<ContentSubject, Integer> {
    // Có thể thêm tìm kiếm theo tên sau này
    Optional<ContentSubject> findByMainName(String mainName);
}