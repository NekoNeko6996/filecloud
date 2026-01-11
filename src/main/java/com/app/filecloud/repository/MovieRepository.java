package com.app.filecloud.repository;

import com.app.filecloud.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, String> {
    // Hàm tìm kiếm và phân trang cho trang List
    Page<Movie> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}