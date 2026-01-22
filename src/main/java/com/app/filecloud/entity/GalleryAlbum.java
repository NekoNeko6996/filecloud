package com.app.filecloud.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "gallery_albums")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GalleryAlbum implements Persistable<String> {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "artist_id")
    private Integer artistId; // Có thể map @ManyToOne tới ContentSubject nếu cần

    @Column(nullable = false)
    private String title;

    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy_mode")
    @Builder.Default
    private PrivacyMode privacyMode = PrivacyMode.PRIVATE;

    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", insertable = false, updatable = false)
    @ToString.Exclude
    private ContentSubject artist;
    
    // Ảnh bìa - Quan hệ OneToOne (hoặc ManyToOne) tới GalleryPhoto
    // Dùng FetchType.LAZY để tránh load thừa dữ liệu
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_photo_id")
    @ToString.Exclude
    private GalleryPhoto coverPhoto;

    @Column(name = "created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @Transient
    private List<GalleryPhoto> previewPhotos = new ArrayList<>();

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.updatedAt == null) this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public enum PrivacyMode {
        PRIVATE, PUBLIC, PASSWORD
    }
}