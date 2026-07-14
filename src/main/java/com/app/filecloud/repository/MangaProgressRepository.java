package com.app.filecloud.repository;

import com.app.filecloud.entity.MangaProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MangaProgressRepository extends JpaRepository<MangaProgress, String> {
    Optional<MangaProgress> findByUserIdAndMangaId(UUID userId, String mangaId);
    List<MangaProgress> findByUserId(UUID userId);
}
