package com.app.filecloud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanProgressDto {
    private String subject;       // Tên Subject đang xử lý
    private String fileName;      // Tên file hiện tại
    private String status;        // Trạng thái: "Hashing", "Importing", "Checking"...
    private int filePercent;      // % hoàn thành của file hiện tại (Hashing)
    private int currentFileIndex; // Số thứ tự file
    private int totalFiles;       // Tổng số file trong folder
}