package com.app.filecloud.controller;

import com.app.filecloud.entity.ContentSubject;
import com.app.filecloud.entity.FileNode;
import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.repository.ContentSubjectRepository;
import com.app.filecloud.repository.FileNodeRepository;
import com.app.filecloud.repository.StorageVolumeRepository;
import com.app.filecloud.repository.SubjectFolderMappingRepository;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ContentSubjectRepository subjectRepository;
    private final FileNodeRepository fileNodeRepository;
    private final SubjectFolderMappingRepository mappingRepository;
    private final StorageVolumeRepository volumeRepository;

    @GetMapping("/")
    public String dashboard(Model model) {
        List<ContentSubject> recentSubjects = subjectRepository.findAll(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedAt"))
        ).getContent();

        List<FileNode> recentMedia = fileNodeRepository.findByType(
                FileNode.Type.FILE,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        
        List<StorageVolume> activeVolumes = volumeRepository.findVolumesInUse();
        model.addAttribute("activeVolumes", activeVolumes);
        model.addAttribute("recentSubjects", recentSubjects);
        model.addAttribute("recentFiles", recentMedia);

        return "dashboard";
    }
    
    // === API MỚI: LẤY SUBJECT CỦA FILE ===
    @GetMapping("/api/file/{fileId}/subject")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFileSubject(@PathVariable String fileId) {
        FileNode file = fileNodeRepository.findById(fileId).orElse(null);
        Map<String, Object> response = new HashMap<>();

        if (file != null && file.getSubjectMappingId() != null) {
            // Tìm Mapping -> Lấy Subject
            mappingRepository.findById(file.getSubjectMappingId()).ifPresent(mapping -> {
                ContentSubject sub = mapping.getSubject();
                response.put("id", sub.getId());
                response.put("name", sub.getMainName());
                response.put("avatarUrl", sub.getAvatarUrl());
            });
        }
        // Nếu không có mapping (file rác hoặc lỗi), trả về rỗng hoặc null
        return ResponseEntity.ok(response);
    }
}
