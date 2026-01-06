package com.app.filecloud.controller;

import com.app.filecloud.entity.ContentSubject;
import com.app.filecloud.entity.FileNode;
import com.app.filecloud.entity.FileTag;
import com.app.filecloud.entity.Tag;
import com.app.filecloud.repository.FileNodeRepository;
import com.app.filecloud.repository.FileSubjectsRepository;
import com.app.filecloud.repository.FileTagRepository;
import com.app.filecloud.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoPageController {

    private final FileNodeRepository fileNodeRepository;
    private final FileSubjectsRepository fileSubjectsRepository;
    private final FileTagRepository fileTagRepository;
    private final TagRepository tagRepository;

    @GetMapping("/{id}")
    public String watchVideo(@PathVariable String id, Model model) {
        // 1. Lấy thông tin File hiện tại
        FileNode file = fileNodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found"));

        // 2. Lấy Subject chính
        ContentSubject subject = fileSubjectsRepository.findMainSubjectByFileId(id)
                .orElse(null);

        // 3. Chuẩn bị dữ liệu Playlist & Tags
        List<FileNode> playlist = List.of();
        List<Tag> subjectTags = List.of();
        Map<String, String> fileTagMap = new HashMap<>(); // Map <FileId, "1 3 5">

        if (subject != null) {
            // A. Lấy toàn bộ video của Subject (Bao gồm cả video hiện tại)
            playlist = fileNodeRepository.findBySubjectId(subject.getId())
                    .stream()
                    .filter(FileNode::isVideo) // Chỉ lấy Video
                    // .filter(f -> !f.getId().equals(id)) // <-- ĐÃ BỎ LỌC, LẤY HẾT
                    .toList();

            // B. Lấy Tags của Subject và Map vào từng File
            List<String> allFileIds = playlist.stream().map(FileNode::getId).toList();
            if (!allFileIds.isEmpty()) {
                List<FileTag> allFileTags = fileTagRepository.findByFileIdIn(allFileIds);

                // Lấy danh sách Tag Unique để hiển thị bộ lọc
                Set<Integer> tagIds = allFileTags.stream().map(FileTag::getTagId).collect(Collectors.toSet());
                subjectTags = tagRepository.findAllById(tagIds);

                // Map FileId -> String TagIds (VD: "tag1 tag5 tag8")
                for (FileNode f : playlist) {
                    String tagsStr = allFileTags.stream()
                            .filter(ft -> ft.getFileId().equals(f.getId()))
                            .map(ft -> String.valueOf(ft.getTagId()))
                            .collect(Collectors.joining(" "));
                    fileTagMap.put(f.getId(), tagsStr);
                }
            }
        }

        // 4. Random Videos (Gợi ý bên dưới)
        List<FileNode> randomVideos = fileNodeRepository.findRandomVideos(id, 20);

        model.addAttribute("file", file);
        model.addAttribute("subject", subject);
        model.addAttribute("playlist", playlist); // Đổi tên biến recommendations -> playlist cho đúng nghĩa
        model.addAttribute("subjectTags", subjectTags);
        model.addAttribute("fileTagMap", fileTagMap); // Dữ liệu cho JS lọc
        model.addAttribute("randomVideos", randomVideos);

        return "video-view";
    }
}
