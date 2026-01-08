package com.app.filecloud.repository;

import com.app.filecloud.entity.MangaSeries;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MangaSeriesRepository extends JpaRepository<MangaSeries, String> {
    // Tìm truyện theo tên (Search)
    List<MangaSeries> findByTitleContainingIgnoreCase(String title);
    
    // Tìm theo trạng thái (ONGOING, COMPLETED...)
    List<MangaSeries> findByStatus(MangaSeries.Status status);
}