package com.app.filecloud.repository;

import com.app.filecloud.entity.MangaPage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MangaPageRepository extends JpaRepository<MangaPage, String> {
    // Lấy tất cả trang của 1 chapter, sắp xếp theo thứ tự trang (QUAN TRỌNG khi đọc truyện)
    List<MangaPage> findByChapterIdOrderByPageOrderAsc(String chapterId);
    
    // Đếm số trang của 1 chapter
    long countByChapterId(String chapterId);
}