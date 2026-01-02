package com.app.filecloud.repository;

import com.app.filecloud.entity.MediaMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaMetadataRepository extends JpaRepository<MediaMetadata, String>{
}