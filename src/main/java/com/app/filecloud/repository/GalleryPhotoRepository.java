package com.app.filecloud.repository;

import com.app.filecloud.entity.GalleryPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, String> {
    List<GalleryPhoto> findByAlbumIdOrderByCreatedAtDesc(String albumId);
    
    @Query("SELECT count(p) FROM GalleryPhoto p WHERE p.album.id = :albumId")
    long countByAlbumId(String albumId);
    
    Optional<GalleryPhoto> findFirstByFileHash(String fileHash);
}