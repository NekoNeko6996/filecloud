package com.app.filecloud.repository;

import com.app.filecloud.entity.ContentSubject;
import com.app.filecloud.entity.FileSubject;
import com.app.filecloud.entity.FileSubjectId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FileSubjectsRepository extends JpaRepository<FileSubject, FileSubjectId> {
    // Bạn có thể thêm các hàm tìm kiếm nếu cần, ví dụ:
    // List<FileSubject> findBySubjectId(Integer subjectId);
    @Query("SELECT s FROM ContentSubject s JOIN FileSubject fs ON s.id = fs.subjectId WHERE fs.fileId = :fileId AND fs.isMainOwner = true")
    Optional<ContentSubject> findMainSubjectByFileId(String fileId);
}