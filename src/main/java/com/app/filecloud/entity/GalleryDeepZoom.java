package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "gallery_deep_zoom")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GalleryDeepZoom implements Persistable<String> {

    @Id
    @Column(name = "photo_id", columnDefinition = "CHAR(36)")
    private String photoId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "photo_id")
    @ToString.Exclude
    private GalleryPhoto photo;

    @Column(name = "tile_path_root", nullable = false)
    private String tilePathRoot;

    @Column(name = "max_level")
    private Integer maxLevel;

    @Column(name = "tile_size")
    @Builder.Default
    private Integer tileSize = 256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

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

    public enum Status {
        PENDING, PROCESSING, READY, FAILED
    }
}