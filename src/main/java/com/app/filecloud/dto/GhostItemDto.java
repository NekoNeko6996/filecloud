package com.app.filecloud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GhostItemDto {
    private String id;
    private String itemType; // "FILE_NODE" or "GALLERY_PHOTO"
    private String name;
    private String relativePath;
    private String fullPath;
    private long size;
    private String formattedSize;
    private String mimeType;
    private LocalDateTime createdAt;
}
