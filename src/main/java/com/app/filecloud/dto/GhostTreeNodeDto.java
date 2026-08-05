package com.app.filecloud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GhostTreeNodeDto {
    private String id; // File ID for FILE node, or relative path for FOLDER node
    private String name;
    private String relativePath;
    private String nodeType; // "FOLDER" or "FILE"
    private String itemType; // "FILE_NODE", "GALLERY_PHOTO", or null for FOLDER
    private long size;
    private String formattedSize;
    private String mimeType;
    private LocalDateTime createdAt;
    private int ghostCount; // Total ghost files inside folder
    
    @Builder.Default
    private List<GhostTreeNodeDto> children = new ArrayList<>();
}
