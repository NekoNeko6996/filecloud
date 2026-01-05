package com.app.filecloud.controller;

import com.app.filecloud.entity.ContentSubject;
import com.app.filecloud.entity.FileNode;
import com.app.filecloud.entity.SocialPlatform;
import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.entity.SubjectFolderMapping;
import com.app.filecloud.entity.SubjectSocialLink;
import com.app.filecloud.repository.ContentSubjectRepository;
import com.app.filecloud.repository.FileNodeRepository;
import com.app.filecloud.repository.FileTagRepository;
import com.app.filecloud.repository.SocialPlatformRepository;
import com.app.filecloud.repository.SubjectFolderMappingRepository;
import com.app.filecloud.repository.SubjectSocialLinkRepository;
import com.app.filecloud.repository.UserRepository;
import com.app.filecloud.service.FileStorageService;
import com.app.filecloud.service.MediaService;
import com.app.filecloud.service.StorageVolumeService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final ContentSubjectRepository subjectRepository;
    private final FileNodeRepository fileNodeRepository;
    private final MediaService mediaService;
    private final UserRepository userRepository;

    private final SubjectFolderMappingRepository mappingRepository;
    private final FileStorageService fileStorageService;
    private final StorageVolumeService storageVolumeService;
    private final FileTagRepository fileTagRepository;

    private final SocialPlatformRepository platformRepository;
    private final SubjectSocialLinkRepository socialLinkRepository;

    // root path
    @Value("${app.storage.root:uploads}")
    private String rootUploadDir;

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

        List<SubjectFolderMapping> rawMappings = mappingRepository.findBySubjectId(id);

        List<Map<String, Object>> mappingsDto = rawMappings.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("relativePath", m.getRelativePath());

            // Chỉ lấy info Volume cần thiết, tránh lấy cả object Volume to đùng
            Map<String, Object> volMap = new HashMap<>();
            volMap.put("id", m.getVolume().getId());
            volMap.put("label", m.getVolume().getLabel());
            map.put("volume", volMap);

            return map;
        }).collect(Collectors.toList());

        model.addAttribute("mappings", mappingsDto);

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
            if (userId == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

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
        if (volume == null) {
            return ResponseEntity.notFound().build();
        }

        // Quét lại dung lượng thực tế (có thể tốn thời gian nên làm ở đây)
        File root = new File(volume.getMountPoint());
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();

        // Format cho đẹp
        Map<String, Object> data = new HashMap<>();
        data.put("total", formatSize(total));
        data.put("free", formatSize(free));
        data.put("percent", (int) ((double) (total - free) / total * 100));

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

    @GetMapping("/{id}/delete-preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDeletePreview(@PathVariable Integer id) {
        ContentSubject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        List<FileNode> files = fileNodeRepository.findBySubjectId(id);
        long totalSize = files.stream().mapToLong(FileNode::getSize).sum();

        // --- LOGIC MỚI: ĐẾM TAG ---
        long tagCount = 0;
        if (!files.isEmpty()) {
            List<String> fileIds = files.stream().map(FileNode::getId).toList();
            tagCount = fileTagRepository.countByFileIdIn(fileIds);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("name", subject.getMainName());
        response.put("fileCount", files.size());
        response.put("totalSize", formatSize(totalSize));
        response.put("tagCount", tagCount); // <--- Trả về số lượng tag
        response.put("hasAvatar", subject.getAvatarUrl() != null && !subject.getAvatarUrl().isEmpty());

        return ResponseEntity.ok(response);
    }

    // === CẬP NHẬT API DELETE ===
    @PostMapping("/delete")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> deleteSubject(@RequestParam("id") Integer id,
            @RequestParam(value = "deletePhysical", defaultValue = "false") boolean deletePhysical) {
        try {
            ContentSubject subject = subjectRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

            // Lấy danh sách file
            List<FileNode> files = fileNodeRepository.findBySubjectId(id);

            // 1. VÒNG LẶP CHỈ ĐỂ XÓA FILE VẬT LÝ (KHÔNG THAO TÁC DB Ở ĐÂY)
            for (FileNode file : files) {
                // Xóa file vật lý trên ổ cứng
                if (deletePhysical) {
                    StorageVolume vol = storageVolumeService.getVolumeById(file.getVolumeId());
                    if (vol != null) {
                        try {
                            Path physicalPath = Paths.get(vol.getMountPoint(), file.getRelativePath());
                            Files.deleteIfExists(physicalPath);
                        } catch (Exception e) {
                            /* Ignore lỗi file hệ thống */ }
                    }
                }

                // Xóa Thumbnail rác
                try {
                    Path thumbDir = Paths.get(rootUploadDir, ".cache", "thumbnails");
                    Files.deleteIfExists(thumbDir.resolve(file.getId() + "_small.jpg"));
                    Files.deleteIfExists(thumbDir.resolve(file.getId() + "_medium.jpg"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 2. XÓA DỮ LIỆU DB BẰNG BATCH (QUAN TRỌNG)
            // Thay vì delete từng cái gây lỗi Hibernate, ta xóa 1 lần.
            // DB sẽ tự động xóa MediaMetadata, FileTag, FileSubject nhờ ON DELETE CASCADE
            if (!files.isEmpty()) {
                fileNodeRepository.deleteAllInBatch(files);
            }

            // 3. Xóa Avatar Subject
            if (subject.getAvatarUrl() != null && subject.getAvatarUrl().startsWith("/avatars/")) {
                try {
                    String relativeAvatarPath = subject.getAvatarUrl().startsWith("/")
                            ? subject.getAvatarUrl().substring(1)
                            : subject.getAvatarUrl();
                    Files.deleteIfExists(Paths.get(rootUploadDir, relativeAvatarPath));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 4. Xóa Subject
            subjectRepository.delete(subject);

            return ResponseEntity.ok("Deleted successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error deleting: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/socials")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getSocialLinks(@PathVariable Integer id) {
        List<SubjectSocialLink> links = socialLinkRepository.findBySubjectId(id);

        List<Map<String, Object>> response = links.stream().map(link -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", link.getId());
            map.put("platformId", link.getPlatform().getId());
            map.put("platformName", link.getPlatform().getName());
            map.put("iconUrl", link.getPlatform().getIconUrl());
            map.put("profilePath", link.getProfilePath());
            map.put("fullUrlOverride", link.getFullUrlOverride());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // 2. Lấy danh sách tất cả Platform (để nạp vào dropdown)
    @GetMapping("/platforms")
    @ResponseBody
    public ResponseEntity<List<SocialPlatform>> getAllPlatforms() {
        return ResponseEntity.ok(platformRepository.findAll());
    }

    // 3. Lưu (Thêm mới hoặc Cập nhật) Link
    @PostMapping("/socials/save")
    @ResponseBody
    public ResponseEntity<String> saveSocialLink(@RequestParam("subjectId") Integer subjectId,
            @RequestParam(value = "linkId", required = false) Long linkId,
            @RequestParam("platformId") Integer platformId,
            @RequestParam("profilePath") String profilePath,
            @RequestParam(value = "fullUrl", required = false) String fullUrl) {
        try {
            SubjectSocialLink link;
            if (linkId != null) {
                link = socialLinkRepository.findById(linkId).orElseThrow();
            } else {
                link = new SubjectSocialLink();
                ContentSubject subject = subjectRepository.findById(subjectId).orElseThrow();
                link.setSubject(subject);
            }

            SocialPlatform platform = platformRepository.findById(platformId).orElseThrow();
            link.setPlatform(platform);
            link.setProfilePath(profilePath);
            link.setFullUrlOverride(fullUrl != null && !fullUrl.trim().isEmpty() ? fullUrl : null);

            socialLinkRepository.save(link);
            return ResponseEntity.ok("Saved successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error saving: " + e.getMessage());
        }
    }

    // 4. Xóa Link
    @PostMapping("/socials/delete")
    @ResponseBody
    public ResponseEntity<String> deleteSocialLink(@RequestParam("linkId") Long linkId) {
        try {
            socialLinkRepository.deleteById(linkId);
            return ResponseEntity.ok("Deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error deleting");
        }
    }

    @PostMapping("/media/delete")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> deleteSubjectMedia(@RequestBody Map<String, Object> payload) {
        try {
            // 1. Parse dữ liệu từ Client
            List<String> fileIds = (List<String>) payload.get("fileIds");
            boolean deletePhysical = (Boolean) payload.getOrDefault("deletePhysical", false);

            if (fileIds == null || fileIds.isEmpty()) {
                return ResponseEntity.badRequest().body("No files selected");
            }

            // 2. Lấy danh sách FileNode từ DB
            List<FileNode> files = fileNodeRepository.findAllById(fileIds);

            for (FileNode file : files) {
                // A. Xóa File Vật lý (Chỉ khi user chọn)
                if (deletePhysical) {
                    StorageVolume vol = storageVolumeService.getVolumeById(file.getVolumeId());
                    if (vol != null) {
                        try {
                            Path physicalPath = Paths.get(vol.getMountPoint(), file.getRelativePath());
                            Files.deleteIfExists(physicalPath);
                        } catch (Exception e) {
                            System.err.println("Lỗi xóa file vật lý: " + file.getName());
                        }
                    }
                }

                // B. LUÔN LUÔN Xóa Thumbnail (Dù có xóa vật lý hay không)
                try {
                    Path thumbDir = Paths.get(rootUploadDir, ".cache", "thumbnails");
                    Files.deleteIfExists(thumbDir.resolve(file.getId() + "_small.jpg"));
                    Files.deleteIfExists(thumbDir.resolve(file.getId() + "_medium.jpg"));
                    // Xóa luôn thư mục temp_frames nếu còn sót
                    Files.deleteIfExists(Paths.get(rootUploadDir, ".cache", "temp_frames", file.getId() + "_source.jpg"));
                } catch (Exception e) {
                    // Ignore error
                }
            }

            // 3. Xóa dữ liệu trong DB (Dùng batch để tự động cascade xóa Metadata, Tag, FileSubject)
            fileNodeRepository.deleteAllInBatch(files);

            return ResponseEntity.ok("Deleted " + files.size() + " files successfully");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error deleting media: " + e.getMessage());
        }
    }
}
