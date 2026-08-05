package com.app.filecloud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GhostScanResultDto {
    private Integer volumeId;
    private String volumeLabel;
    private String mountPoint;
    private boolean isOnline;
    private long totalDbFiles;
    private long ghostCount;
    private long totalGhostSize;
    private String formattedGhostSize;
    
    @Builder.Default
    private List<GhostItemDto> ghostItems = new ArrayList<>();

    @Builder.Default
    private List<GhostTreeNodeDto> ghostTree = new ArrayList<>();
}

