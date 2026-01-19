package com.app.filecloud.entity;

import jakarta.persistence.*;
import java.text.DecimalFormat;
import lombok.*;

import java.time.LocalDateTime;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "file_nodes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileNode implements Persistable<String> {

    @Id
    // @UuidGenerator
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "parent_id")
    private String parentId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type; // FILE, FOLDER

    @Column(name = "subject_mapping_id")
    private Integer subjectMappingId;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    private long size; // Bytes

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "owner_id", nullable = false)
    private String ownerId; // UUID của User

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @Column(name = "volume_id")
    private Integer volumeId;

    @Column(name = "relative_path")
    private String relativePath;

    @Column(name = "file_hash")
    private String fileHash;

    @OneToOne(mappedBy = "fileNode", fetch = FetchType.LAZY)
    @ToString.Exclude
    private MediaMetadata metadata;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Type {
        FILE, FOLDER
    }

    // Helper để check nhanh
    public boolean isFolder() {
        return this.type == Type.FOLDER;
    }

    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public boolean isVideo() {
        return mimeType != null && mimeType.startsWith("video/");
    }

    public String getReadableSize() {
        if (this.size <= 0) {
            return "0 MB";
        }
        // Tính ra MB, format 2 số thập phân
        double sizeInMb = this.size / 1048576.0;
        return new DecimalFormat("#.##").format(sizeInMb) + " MB";
    }

    public String getDurationFormatted() {
        // Kiểm tra null safety
        if (this.metadata == null || this.metadata.getDurationSeconds() == null
                || this.metadata.getDurationSeconds() == 0) {
            return null;
        }

        int seconds = this.metadata.getDurationSeconds();
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s); // VD: 1:05:20
        } else {
            return String.format("%02d:%02d", m, s); // VD: 05:20
        }
    }

    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
