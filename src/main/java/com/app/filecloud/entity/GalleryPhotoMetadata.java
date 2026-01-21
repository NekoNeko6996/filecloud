package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "gallery_photo_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GalleryPhotoMetadata implements Persistable<String> {

    @Id
    @Column(name = "photo_id", columnDefinition = "CHAR(36)")
    private String photoId;

    // MapsId giúp entity này dùng chung ID với GalleryPhoto
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "photo_id")
    @ToString.Exclude
    private GalleryPhoto photo;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "camera_make")
    private String cameraMake;

    @Column(name = "camera_model")
    private String cameraModel;

    @Column(name = "lens_model")
    private String lensModel;

    private Integer iso;

    private Double aperture; // f-stop

    @Column(name = "shutter_speed")
    private String shutterSpeed;

    @Column(name = "focal_length")
    private Double focalLength;

    @Column(name = "gps_lat")
    private Double gpsLat;

    @Column(name = "gps_lng")
    private Double gpsLng;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public String getId() {
        return photoId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
    
    // Helper để hiển thị thông số đẹp
    public String getExifSummary() {
        StringBuilder sb = new StringBuilder();
        if (aperture != null) sb.append("f/").append(aperture).append(" ");
        if (shutterSpeed != null) sb.append(shutterSpeed).append("s ");
        if (iso != null) sb.append("ISO").append(iso);
        return sb.toString().trim();
    }
}