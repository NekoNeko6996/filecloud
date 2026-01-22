package com.app.filecloud.repository;

import com.app.filecloud.entity.GalleryPhotoMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GalleryPhotoMetadataRepository extends JpaRepository<GalleryPhotoMetadata, String> {
    // Basic CRUD is enough
}