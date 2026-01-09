package com.app.filecloud.repository;

import com.app.filecloud.entity.MangaPage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MangaPageRepository extends JpaRepository<MangaPage, String> {

    // Lấy tất cả trang của 1 chapter, sắp xếp theo thứ tự trang (QUAN TRỌNG khi đọc truyện)
    List<MangaPage> findByChapterIdOrderByPageOrderAsc(String chapterId);

    // Đếm số trang của 1 chapter
    long countByChapterId(String chapterId);

    Optional<MangaPage> findFirstByChapterIdOrderByPageOrderAsc(String chapterId);

    @Modifying
    @Query("UPDATE MangaPage p SET p.pageOrder = :order WHERE p.id = :id")
    void updatePageOrder(@Param("id") String id, @Param("order") int order);
}
