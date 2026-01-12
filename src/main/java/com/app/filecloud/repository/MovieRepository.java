package com.app.filecloud.repository;

import com.app.filecloud.entity.Movie;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, String>, JpaSpecificationExecutor<Movie> {
    // Hàm tìm kiếm và phân trang cho trang List
    Page<Movie> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    
    @Query("SELECT DISTINCT m.releaseYear FROM Movie m WHERE m.releaseYear IS NOT NULL ORDER BY m.releaseYear DESC")
    List<Integer> findDistinctReleaseYears();
}