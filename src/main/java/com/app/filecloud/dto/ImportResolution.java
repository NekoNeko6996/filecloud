package com.app.filecloud.dto;

import lombok.Data;
import java.util.List;

@Data
public class ImportResolution {
    private List<String> safePaths; // Các folder không có trùng lặp, import thẳng
    private List<FileAction> actions; // Các hành động giải quyết trùng lặp

    @Data
    public static class FileAction {
        private String action; // KEEP_OLD, KEEP_NEW, KEEP_BOTH
        private String existingFileId;
        private String newFilePath;
    }
}