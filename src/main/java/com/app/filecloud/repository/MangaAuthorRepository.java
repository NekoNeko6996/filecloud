package com.app.filecloud.repository;

import com.app.filecloud.entity.MangaAuthor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MangaAuthorRepository extends JpaRepository<MangaAuthor, Integer> {
    // Tìm tác giả theo tên (để tránh tạo trùng)
    Optional<MangaAuthor> findByName(String name);
}