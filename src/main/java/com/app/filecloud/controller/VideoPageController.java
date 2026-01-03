package com.app.filecloud.controller;

import com.app.filecloud.entity.ContentSubject;
import com.app.filecloud.entity.FileNode;
import com.app.filecloud.repository.FileNodeRepository;
import com.app.filecloud.repository.FileSubjectsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoPageController {

    private final FileNodeRepository fileNodeRepository;
    private final FileSubjectsRepository fileSubjectsRepository;

    @GetMapping("/{id}")
    public String watchVideo(@PathVariable String id, Model model) {
        // 1. Lấy thông tin File
        FileNode file = fileNodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found"));

        // 2. Lấy Subject chính của Video này
        ContentSubject subject = fileSubjectsRepository.findMainSubjectByFileId(id)
                .orElse(null); // Có thể null nếu file chưa được map

        // 3. Lấy danh sách gợi ý (Các file khác cùng Subject)
        List<FileNode> recommendations = List.of();
        if (subject != null) {
            // Lấy 10 file mới nhất của Subject này, trừ file đang xem
            recommendations = fileNodeRepository.findBySubjectId(subject.getId())
                    .stream()
                    .filter(f -> !f.getId().equals(id) && f.isVideo())
                    .limit(10)
                    .toList();
        }

        model.addAttribute("file", file);
        model.addAttribute("subject", subject);
        model.addAttribute("recommendations", recommendations);

        return "video-view";
    }
}