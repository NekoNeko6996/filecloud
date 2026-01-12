package com.app.filecloud.repository;

import com.app.filecloud.entity.Studio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StudioRepository extends JpaRepository<Studio, String> {
    Optional<Studio> findByName(String name);
    Optional<Studio> findBySlug(String slug);
}