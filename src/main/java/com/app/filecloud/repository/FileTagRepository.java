package com.app.filecloud.repository;

import com.app.filecloud.entity.FileTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FileTagRepository extends JpaRepository<FileTag, Integer> {
    // Xóa toàn bộ tag của một file (dùng khi vòng lặp xóa từng file)
    void deleteByFileId(String fileId);
    
    // Đếm số lượng tag của danh sách file (Dùng cho API Preview để báo user biết sắp xóa bao nhiêu tag)
    long countByFileIdIn(List<String> fileIds);
}