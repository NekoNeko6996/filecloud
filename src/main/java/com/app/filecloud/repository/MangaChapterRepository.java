package com.app.filecloud.repository;

import com.app.filecloud.entity.MangaChapter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MangaChapterRepository extends JpaRepository<MangaChapter, String> {
    // Lấy danh sách chapter của 1 truyện, sắp xếp theo tên hoặc ngày tạo
    // (Lưu ý: Sắp xếp theo tên string như "Chap 1", "Chap 10" có thể không đúng thứ tự số học, 
    // cần xử lý thêm hoặc dùng field 'chapterNumber' nếu có)
    List<MangaChapter> findByMangaIdOrderByChapterNameAsc(String mangaId);
    
    // Tìm chapter theo Manga và Tên (để check trùng khi upload)
    MangaChapter findByMangaIdAndChapterName(String mangaId, String chapterName);
    
    @Modifying
    @Query("UPDATE MangaChapter c SET c.chapterOrder = :order WHERE c.id = :id")
    void updateChapterOrder(@Param("id") String id, @Param("order") Integer order);
}