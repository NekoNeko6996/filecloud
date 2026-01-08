package com.app.filecloud.dto;

import com.app.filecloud.entity.SubjectSocialLink;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubjectCardDTO {
    private Integer id;
    private String mainName;
    private String aliasName1;
    private String aliasName2;
    private String avatarUrl;
    private LocalDateTime updatedAt;
    
    // Các trường tính toán
    private Long fileCount;
    private Long totalSize; // bytes
    
    // Để hiển thị icon social
    private List<SubjectSocialLink> socialLinks;

    public String getReadableSize() {
        if (this.totalSize == null || this.totalSize <= 0) return "0 MB";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(totalSize) / Math.log10(1024));
        return new java.text.DecimalFormat("#,##0.#").format(totalSize / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}