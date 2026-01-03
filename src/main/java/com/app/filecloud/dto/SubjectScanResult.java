package com.app.filecloud.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SubjectScanResult {
    private String folderName;      // VD: [Name][Alias]
    private String fullPath;        // VD: F:/VIDEO/[Name][Alias]
    private String parsedMainName;  // VD: Name
    private List<String> parsedAliases; // VD: [Alias]
    private int mediaCount;         // Số lượng file video bên trong
    private boolean existsInDb;     // Subject này đã có trong DB chưa?
}