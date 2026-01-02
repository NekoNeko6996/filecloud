package com.app.filecloud.entity;

import jakarta.persistence.*;
import java.text.DecimalFormat;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_nodes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileNode {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "parent_id")
    private String parentId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type; // FILE, FOLDER

    private long size; // Bytes

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "owner_id", nullable = false)
    private String ownerId; // UUID của User

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "volume_id")
    private Integer volumeId; 
    
    @Column(name = "relative_path")
    private String relativePath;
    
    @Column(name = "file_hash")
    private String fileHash;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Type {
        FILE, FOLDER
    }
    
    // Helper để check nhanh
    public boolean isFolder() { return this.type == Type.FOLDER; }
    public boolean isImage() { return mimeType != null && mimeType.startsWith("image/"); }
    public boolean isVideo() { return mimeType != null && mimeType.startsWith("video/"); }
    public String getReadableSize() {
        if (this.size <= 0) return "0 MB";
        // Tính ra MB, format 2 số thập phân
        double sizeInMb = this.size / 1048576.0;
        return new DecimalFormat("#.##").format(sizeInMb) + " MB";
    }
}