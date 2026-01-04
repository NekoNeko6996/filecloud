package com.app.filecloud.controller;

import com.app.filecloud.entity.ContentSubject;
import com.app.filecloud.entity.FileNode;
import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.entity.SubjectFolderMapping;
import com.app.filecloud.repository.ContentSubjectRepository;
import com.app.filecloud.repository.FileNodeRepository;
import com.app.filecloud.repository.SubjectFolderMappingRepository;
import com.app.filecloud.repository.UserRepository;
import com.app.filecloud.service.FileStorageService;
import com.app.filecloud.service.MediaScanService;
import com.app.filecloud.service.MediaService;
import com.app.filecloud.service.ScanProgressService;
import com.app.filecloud.service.StorageVolumeService;
import java.io.File;
import java.text.DecimalFormat;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final ContentSubjectRepository subjectRepository;
    private final FileNodeRepository fileNodeRepository;
    private final MediaService mediaService;
    private final MediaScanService mediaScanService;
    private final ScanProgressService progressService;
    private final UserRepository userRepository;
    
    private final SubjectFolderMappingRepository mappingRepository;
    private final FileStorageService fileStorageService;
    private final StorageVolumeService storageVolumeService;
    

    @GetMapping
    public String subjectsPage(Model model) {
        List<ContentSubject> subjects = subjectRepository.findAll();
        model.addAttribute("subjects", subjects);
        return "subjects";
    }

    @GetMapping("/{id}")
    public String subjectProfilePage(@PathVariable("id") Integer id, Model model) {
        // 1. Lấy thông tin Subject
        ContentSubject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        // 2. Lấy danh sách file media
        List<FileNode> files = fileNodeRepository.findBySubjectId(id);

        // 3. Tính toán thống kê
        long totalSize = files.stream().mapToLong(FileNode::getSize).sum();
        String formattedSize = formatSize(totalSize);
        
        List<SubjectFolderMapping> mappings = mappingRepository.findBySubjectId(id);
        model.addAttribute("mappings", mappings);

        // 4. Đẩy dữ liệu ra View
        model.addAttribute("subject", subject);
        model.addAttribute("files", files);
        model.addAttribute("totalFiles", files.size());
        model.addAttribute("totalSize", formattedSize);

        return "subject-profile";
    }

    // API tạo nhanh Subject
    @PostMapping("/create")
    public String createSubject(@RequestParam("name") String name) {
        ContentSubject subject = ContentSubject.builder().mainName(name).build();
        subjectRepository.save(subject);
        return "redirect:/subjects";
    }

    @PostMapping("/update")
    public String updateSubject(@ModelAttribute ContentSubject subject,
            @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile) {

        ContentSubject existing = subjectRepository.findById(subject.getId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid subject Id:" + subject.getId()));

        existing.setMainName(subject.getMainName());
        existing.setAliasName1(subject.getAliasName1());
        existing.setAliasName2(subject.getAliasName2());
        existing.setDescription(subject.getDescription());

        // LOGIC XỬ LÝ AVATAR
        // 1. Ưu tiên File Upload trước
        if (avatarFile != null && !avatarFile.isEmpty()) {
            String avatarPath = mediaService.saveAvatar(avatarFile);
            existing.setAvatarUrl(avatarPath);
        } // 2. Nếu không upload file nhưng có link URL (người dùng paste link)
        else if (subject.getAvatarUrl() != null && !subject.getAvatarUrl().isEmpty()) {
            existing.setAvatarUrl(subject.getAvatarUrl());
        }
        // 3. Nếu cả 2 đều trống -> Giữ nguyên avatar cũ (không làm gì)

        subjectRepository.save(existing);

        return "redirect:/subjects/" + subject.getId();
    }
    
    @PostMapping("/upload-media")
    @ResponseBody
    public ResponseEntity<String> uploadMedia(@RequestParam("file") MultipartFile file, 
                                              @RequestParam("mappingId") Integer mappingId) {
        try {
            String userId = getCurrentUserId(); // Hàm helper có sẵn
            if (userId == null) return ResponseEntity.status(401).body("Unauthorized");
            
            fileStorageService.uploadFileToMapping(file, mappingId, userId);
            return ResponseEntity.ok("Uploaded: " + file.getOriginalFilename());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
    
    @GetMapping("/check-capacity/{volumeId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkVolumeCapacity(@PathVariable Integer volumeId) {
        StorageVolume volume = storageVolumeService.getVolumeById(volumeId);
        if (volume == null) return ResponseEntity.notFound().build();

        // Quét lại dung lượng thực tế (có thể tốn thời gian nên làm ở đây)
        File root = new File(volume.getMountPoint());
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        
        // Format cho đẹp
        Map<String, Object> data = new HashMap<>();
        data.put("total", formatSize(total));
        data.put("free", formatSize(free));
        data.put("percent", (int)((double)(total - free) / total * 100));
        
        return ResponseEntity.ok(data);
    }

    // Helper format dung lượng
    private String formatSize(long size) {
        if (size <= 0) {
            return "0 MB";
        }
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
    
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return userRepository.findByUsername(auth.getName())
                    .map(user -> user.getId().toString())
                    .orElse(null);
        }
        return null;
    }
}
