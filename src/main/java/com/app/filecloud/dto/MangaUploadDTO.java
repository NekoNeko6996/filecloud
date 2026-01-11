package com.app.filecloud.dto;

import com.app.filecloud.entity.MangaSeries;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Set;

@Data
public class MangaUploadDTO {
    private String title;
    private String description;
    private MangaSeries.Status status;
    private String authorId; // Chỉ nhận ID của tác giả đã chọn
    private Set<String> tagIds;
    private MultipartFile coverFile;

    // Danh sách file ZIP chapter
    private List<MultipartFile> chapterFiles;

    // Chuỗi JSON chứa metadata chapter (tên, thứ tự) từ frontend gửi lên
    // Ví dụ: [{"fileName": "chap1.zip", "newChapterName": "Chapter 1: Mở đầu", "order": 0}, ...]
    private String chapterMetadataJson;
}