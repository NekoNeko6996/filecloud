package com.app.filecloud.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DuplicateFileDTO {
    private String fileName;
    private String existingPath;
    private String newPath;
    private Long size;
}