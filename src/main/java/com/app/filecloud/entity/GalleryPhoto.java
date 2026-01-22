package com.app.filecloud.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.text.DecimalFormat;
import java.time.LocalDateTime;

@Entity
@Table(name = "gallery_photos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GalleryPhoto implements Persistable<String> {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    // Quan hệ cha-con với Album
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    @ToString.Exclude
    private GalleryAlbum album;

    @Column(name = "volume_id", nullable = false)
    private Integer volumeId;

    @Column(name = "uploader_id", nullable = false)
    private String uploaderId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "file_size")
    private long size; // Bytes

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_hash")
    private String fileHash;

    private Integer width;
    private Integer height;
    private String blurhash;

    @Column(name = "is_processed")
    @Builder.Default
    private boolean isProcessed = false;

    @Column(name = "created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Quan hệ 1-1 với Metadata (MapsId vì dùng chung ID)
    @OneToOne(mappedBy = "photo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @PrimaryKeyJoinColumn
    @ToString.Exclude
    private GalleryPhotoMetadata metadata;

    // Quan hệ 1-1 với DeepZoom Data
    @OneToOne(mappedBy = "photo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @PrimaryKeyJoinColumn
    @ToString.Exclude
    private GalleryDeepZoom deepZoom;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
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

    // Helper methods (giống FileNode)
    public String getReadableSize() {
        if (this.size <= 0) return "0 MB";
        double sizeInMb = this.size / 1048576.0;
        return new DecimalFormat("#.##").format(sizeInMb) + " MB";
    }

    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }
}