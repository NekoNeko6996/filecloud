package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "file_thumbnails")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileThumbnail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private String fileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "thumb_type")
    private ThumbType type; // SMALL, MEDIUM

    @Column(name = "storage_path")
    private String storagePath;

    private int width;
    private int height;

    public enum ThumbType {
        SMALL, MEDIUM, LARGE
    }
}