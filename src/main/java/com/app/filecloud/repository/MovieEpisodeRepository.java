package com.app.filecloud.repository;

import com.app.filecloud.entity.MovieEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieEpisodeRepository extends JpaRepository<MovieEpisode, String> {
}