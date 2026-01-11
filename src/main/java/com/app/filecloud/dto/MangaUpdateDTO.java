package com.app.filecloud.dto;

import com.app.filecloud.entity.MangaSeries;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.List;

@Data
public class MangaUpdateDTO {
    private String id;
    private String title;
    private String description;
    private MangaSeries.Status status;
    
    // Spring sẽ tự mapping các checkbox có name="tagIds" vào list này
    private List<Integer> tagIds = new ArrayList<>(); 
    
    // Các trường xử lý cover
    private String coverOption; // "file" hoặc "select"
    private MultipartFile coverFile;
    private String coverPageId;
}