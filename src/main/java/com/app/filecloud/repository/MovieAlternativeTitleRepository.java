package com.app.filecloud.repository;

import com.app.filecloud.entity.MovieAlternativeTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieAlternativeTitleRepository extends JpaRepository<MovieAlternativeTitle, String> {
    // Có thể thêm hàm deleteByMovieId nếu cần
}