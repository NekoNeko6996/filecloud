package com.app.filecloud.repository;

import com.app.filecloud.entity.GalleryAlbum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GalleryAlbumRepository extends JpaRepository<GalleryAlbum, String> {
    List<GalleryAlbum> findByUserIdOrderByCreatedAtDesc(String userId);
}

