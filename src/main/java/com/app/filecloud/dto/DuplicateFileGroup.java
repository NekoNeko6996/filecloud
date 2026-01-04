package com.app.filecloud.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DuplicateFileGroup {
    private String subjectName;
    private List<DuplicatePair> pairs;

    @Data
    @Builder
    public static class DuplicatePair {
        // CHỈ DÙNG STRING, TUYỆT ĐỐI KHÔNG DÙNG: private FileNode existingFile;
        private String existingFileId;
        private String existingFileName;
        private String existingPath;
        private String existingSize;

        private String newFilePath;
        private String newFileName;
        private String newSize;
        
        private String hash;
    }
}