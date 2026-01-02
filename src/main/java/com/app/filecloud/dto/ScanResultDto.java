package com.app.filecloud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResultDto {
    private String fileName;
    private String absolutePath;
    private String type; // FILE hoặc FOLDER
    private long size;
    
    // Các trường thông tin trích xuất
    private String detectedSubject;  // Tên chủ thể
    private String detectedAlbum;    // Tên Album (Chỉ áp dụng cho ảnh)
}