package com.app.filecloud.controller;

import com.app.filecloud.dto.SubjectCardDTO;
import com.app.filecloud.entity.ContentSubject;
import com.app.filecloud.entity.FileNode;
import com.app.filecloud.entity.FileTag;
import com.app.filecloud.entity.SocialPlatform;
import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.entity.SubjectFolderMapping;
import com.app.filecloud.entity.SubjectSocialLink;
import com.app.filecloud.entity.Tag;
import com.app.filecloud.repository.ContentSubjectRepository;
import com.app.filecloud.repository.FileNodeRepository;
import com.app.filecloud.repository.FileTagRepository;
import com.app.filecloud.repository.SocialPlatformRepository;
import com.app.filecloud.repository.SubjectFolderMappingRepository;
import com.app.filecloud.repository.SubjectSocialLinkRepository;
import com.app.filecloud.repository.TagRepository;
import com.app.filecloud.repository.UserRepository;
import com.app.filecloud.service.FileStorageService;
import com.app.filecloud.service.MediaService;
import com.app.filecloud.service.StorageVolumeService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
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

    private final TagRepository tagRepository;
    // root path
    @Value("${app.storage.root:uploads}")
    private String rootUploadDir;

    @GetMapping
    public String subjectsPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer platformId,
            @RequestParam(required = false, defaultValue = "name_asc") String sort,
            Model model) {

        // 1. Lấy dữ liệu thô (Object Array) từ DB
        List<Object[]> rawResults = subjectRepository.searchSubjectsWithStats(
                (keyword != null && !keyword.isBlank()) ? keyword : null,
                platformId);

        // 2. Map từ Object[] sang DTO
        List<SubjectCardDTO> subjects = new ArrayList<>();
        for (Object[] row : rawResults) {
            ContentSubject sub = (ContentSubject) row[0];
            Long fileCount = (Long) row[1];
            Long totalSize = (Long) row[2];

            // Tạo DTO thủ công (An toàn hơn dùng JPQL constructor)
            SubjectCardDTO dto = new SubjectCardDTO();
            dto.setId(sub.getId());
            dto.setMainName(sub.getMainName());
            dto.setAliasName1(sub.getAliasName1());
            dto.setAliasName2(sub.getAliasName2());
            dto.setAvatarUrl(sub.getAvatarUrl());
            dto.setUpdatedAt(sub.getUpdatedAt());

            dto.setFileCount(fileCount);
            dto.setTotalSize(totalSize);

            // Hibernate sẽ tự fetch socialLinks khi gọi getter (Lazy Loading)
            // Vì đang trong session (OpenEntityManagerInView mặc định true), điều này hoạt
            // động tốt.
            dto.setSocialLinks(sub.getSocialLinks());

            subjects.add(dto);
        }

        // 3. Xử lý Sắp xếp (In-Memory Sorting)
        switch (sort) {
            case "name_desc" ->
                subjects.sort(
                        Comparator.comparing(SubjectCardDTO::getMainName, String.CASE_INSENSITIVE_ORDER).reversed());
            case "size_desc" ->
                subjects.sort(Comparator.comparing(SubjectCardDTO::getTotalSize).reversed());
            case "size_asc" ->
                subjects.sort(Comparator.comparing(SubjectCardDTO::getTotalSize));
            case "files_desc" ->
                subjects.sort(Comparator.comparing(SubjectCardDTO::getFileCount).reversed());
            case "newest" ->
                subjects.sort(Comparator.comparing(SubjectCardDTO::getUpdatedAt).reversed());
            case "oldest" ->
                subjects.sort(Comparator.comparing(SubjectCardDTO::getUpdatedAt));
            default ->
                subjects.sort(Comparator.comparing(SubjectCardDTO::getMainName, String.CASE_INSENSITIVE_ORDER)); // name_asc
        }

        // 4. Truyền dữ liệu bổ trợ
        List<SocialPlatform> platforms = platformRepository.findAll();

        model.addAttribute("subjects", subjects);
        model.addAttribute("platforms", platforms);

        model.addAttribute("paramKeyword", keyword);
        model.addAttribute("paramPlatformId", platformId);
        model.addAttribute("paramSort", sort);
        model.addAttribute("totalCount", subjects.size());

        return "subjects";
    }

    @GetMapping("/{id}")
    public String subjectProfile(
            @PathVariable Integer id,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            Model model) {
        ContentSubject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        // Lấy danh sách Files
        List<FileNode> files = fileNodeRepository.findBySubjectId(id);

        switch (sort) {
            case "name_asc" ->
                files.sort(Comparator.comparing(FileNode::getName, String.CASE_INSENSITIVE_ORDER));
            case "name_desc" ->
                files.sort(Comparator.comparing(FileNode::getName, String.CASE_INSENSITIVE_ORDER).reversed());
            case "size_desc" ->
                files.sort(Comparator.comparing(FileNode::getSize).reversed());
            case "size_asc" ->
                files.sort(Comparator.comparing(FileNode::getSize));
            case "oldest" ->
                files.sort(Comparator.comparing(FileNode::getCreatedAt));
            case "newest" ->
                files.sort(Comparator.comparing(FileNode::getCreatedAt).reversed());
            default ->
                files.sort(Comparator.comparing(FileNode::getCreatedAt).reversed());
        }

        // --- XỬ LÝ TAGS ---
        List<String> fileIds = files.stream().map(FileNode::getId).toList();
        List<FileTag> allFileTags = fileIds.isEmpty() ? List.of() : fileTagRepository.findByFileIdIn(fileIds);

        Set<Integer> uniqueTagIds = allFileTags.stream().map(FileTag::getTagId).collect(Collectors.toSet());
        List<Tag> subjectTags = tagRepository.findAllById(uniqueTagIds);

        Map<String, String> fileTagMap = new HashMap<>();
        for (FileNode f : files) {
            String tagsStr = allFileTags.stream()
                    .filter(ft -> ft.getFileId().equals(f.getId()))
                    .map(ft -> String.valueOf(ft.getTagId()))
                    .collect(Collectors.joining(" "));
            fileTagMap.put(f.getId(), tagsStr);
        }

        // --- XỬ LÝ MAPPINGS (Cập nhật logic tính size) ---
        List<SubjectFolderMapping> mappingEntities = mappingRepository.findBySubjectId(id);

        List<Map<String, Object>> safeMappings = mappingEntities.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("relativePath", m.getRelativePath());

            // 1. Tính dung lượng thư mục này đang dùng (Tổng size file)
            // Lưu ý: m.getVolume().getId() là ID của ổ đĩa
            long usedByFolder = fileNodeRepository.sumSizeByMappingId(m.getId());
            map.put("folderUsage", usedByFolder);
            map.put("folderUsageFormatted", formatSize(usedByFolder));

            // 2. Thông tin ổ đĩa (để tính % hiển thị nếu cần)
            if (m.getVolume() != null) {
                Map<String, Object> vol = new HashMap<>();
                vol.put("id", m.getVolume().getId());
                vol.put("label", m.getVolume().getLabel());
                vol.put("mountPoint", m.getVolume().getMountPoint());
                vol.put("totalCapacity", m.getVolume().getTotalCapacity());
                vol.put("availableCapacity", m.getVolume().getAvailableCapacity());
                map.put("volume", vol);
            }
            return map;
        }).collect(Collectors.toList());

        Map<Integer, VolumeStats> volumeStatsMap = new HashMap<>();

        if (subject.getFolderMappings() != null) {
            for (SubjectFolderMapping mapping : subject.getFolderMappings()) {
                StorageVolume vol = mapping.getVolume();
                if (vol != null && !volumeStatsMap.containsKey(vol.getId())) {
                    File root = new File(vol.getMountPoint());
                    if (root.exists()) {
                        long total = root.getTotalSpace();
                        long free = root.getFreeSpace();
                        long used = total - free;
                        int percent = (total > 0) ? (int) ((used * 100) / total) : 0;

                        // Tạo object stats
                        VolumeStats stats = new VolumeStats(
                                formatSize(free) + " free",
                                percent,
                                vol.getMountPoint()
                        );
                        volumeStatsMap.put(vol.getId(), stats);
                    } else {
                        // Ổ đĩa bị ngắt kết nối
                        volumeStatsMap.put(vol.getId(), new VolumeStats("Offline", 0, vol.getMountPoint()));
                    }
                }
            }
        }

        model.addAttribute("volumeStatsMap", volumeStatsMap);

        // --- ADD ATTRIBUTES ---
        model.addAttribute("subject", subject);
        model.addAttribute("files", files);

        // Stats
        long totalSize = files.stream().mapToLong(FileNode::getSize).sum();
        model.addAttribute("totalFiles", files.size());
        model.addAttribute("totalSize", formatSize(totalSize));

        // Tags
        model.addAttribute("subjectTags", subjectTags);
        model.addAttribute("fileTagMap", fileTagMap);

        // Mappings (Dùng bản safe đã convert)
        model.addAttribute("mappings", safeMappings);

        model.addAttribute("selectedSort", sort);

        return "subject-profile";
    }
    
    public record VolumeStats(String text, int percent, String mountPoint) {}

    // Helper format size (để tính tổng dung lượng hiển thị)
    private String formatSize(long size) {
        if (size <= 0) {
            return "0 MB";
        }
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " "
                + units[digitGroups];
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
            @SuppressWarnings("unchecked")
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
                    Files.deleteIfExists(
                            Paths.get(rootUploadDir, ".cache", "temp_frames", file.getId() + "_source.jpg"));
                } catch (Exception e) {
                    // Ignore error
                }
            }

            // 3. Xóa dữ liệu trong DB (Dùng batch để tự động cascade xóa Metadata, Tag,
            // FileSubject)
            fileNodeRepository.deleteAllInBatch(files);

            return ResponseEntity.ok("Deleted " + files.size() + " files successfully");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error deleting media: " + e.getMessage());
        }
    }

    @GetMapping("/media/tags")
    @ResponseBody
    public ResponseEntity<List<Tag>> getFileTags(@RequestParam("fileId") String fileId) {
        // Query tìm các Tag dựa trên fileId thông qua bảng trung gian file_tags
        // Lưu ý: Cần thêm hàm findTagsByFileId vào TagRepository hoặc dùng logic dưới
        // đây
        List<FileTag> fileTags = fileTagRepository.findByFileId(fileId);
        List<Integer> tagIds = fileTags.stream().map(FileTag::getTagId).toList();

        if (tagIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(tagRepository.findAllById(tagIds));
    }

    // 2. Tìm kiếm Tag (để user chọn add)
    @GetMapping("/tags/search")
    @ResponseBody
    public ResponseEntity<List<Tag>> searchTags(@RequestParam("q") String query) {
        // Cần thêm hàm findByNameContainingIgnoreCase vào TagRepository
        // return
        // ResponseEntity.ok(tagRepository.findByNameContainingIgnoreCase(query));

        // Demo đơn giản nếu chưa có hàm custom: lấy tất cả rồi lọc (chỉ ổn với data ít)
        List<Tag> all = tagRepository.findAll();
        List<Tag> filtered = all.stream()
                .filter(t -> t.getName().toLowerCase().contains(query.toLowerCase()))
                .limit(10)
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    // 3. Thêm Tag vào File
    @PostMapping("/media/tags/add")
    @ResponseBody
    public ResponseEntity<String> addTagToFile(@RequestParam("fileId") String fileId,
            @RequestParam("tagId") Integer tagId) {
        // Kiểm tra xem đã tồn tại chưa
        if (fileTagRepository.existsByFileIdAndTagId(fileId, tagId)) {
            return ResponseEntity.ok("Already tagged");
        }

        FileTag ft = new FileTag();
        ft.setFileId(fileId);
        ft.setTagId(tagId);
        fileTagRepository.save(ft);
        return ResponseEntity.ok("Added");
    }

    // 4. Xóa Tag khỏi File
    @PostMapping("/media/tags/remove")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> removeTagFromFile(@RequestParam("fileId") String fileId,
            @RequestParam("tagId") Integer tagId) {
        fileTagRepository.deleteByFileIdAndTagId(fileId, tagId);
        return ResponseEntity.ok("Removed");
    }

    // 5. Tạo Tag mới nhanh (Quick Create)
    @PostMapping("/tags/create-quick")
    @ResponseBody
    public ResponseEntity<Tag> createQuickTag(@RequestParam("name") String name) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setSlug(toSlug(name)); // Dùng lại hàm toSlug helper (xem phần dưới)
        tag.setColorHex("#833cf6"); // Màu mặc định
        Tag saved = tagRepository.save(tag);
        return ResponseEntity.ok(saved);
    }

    // Helper tạo slug (copy từ TagController qua nếu chưa có)
    private String toSlug(String input) {
        return input.toLowerCase().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
    }

    @GetMapping("/media/download/{fileId}")
    public ResponseEntity<Resource> downloadSingleFile(@PathVariable String fileId) {
        try {
            FileNode fileNode = fileNodeRepository.findById(fileId).orElseThrow();
            StorageVolume vol = storageVolumeService.getVolumeById(fileNode.getVolumeId());

            if (vol == null) {
                throw new RuntimeException("Volume not found");
            }

            Path path = Paths.get(vol.getMountPoint(), fileNode.getRelativePath());
            Resource resource = new UrlResource(path.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileNode.getName() + "\"")
                        .body(resource);
            } else {
                throw new RuntimeException("Could not read the file!");
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // === 2. API TẢI XUỐNG NHIỀU FILE (ZIP) ===
    @PostMapping("/media/download-batch")
    public void downloadBatchFiles(@RequestBody List<String> fileIds, HttpServletResponse response) {
        try {
            response.setContentType("application/zip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"download_batch.zip\"");

            try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
                List<FileNode> files = fileNodeRepository.findAllById(fileIds);

                for (FileNode fileNode : files) {
                    StorageVolume vol = storageVolumeService.getVolumeById(fileNode.getVolumeId());
                    if (vol != null) {
                        Path path = Paths.get(vol.getMountPoint(), fileNode.getRelativePath());
                        if (Files.exists(path)) {
                            // Tạo entry trong file zip
                            ZipEntry entry = new ZipEntry(fileNode.getName());
                            // Xử lý trùng tên trong zip nếu cần (ở đây làm đơn giản)
                            entry.setSize(Files.size(path));
                            zos.putNextEntry(entry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Handle error (không thể return ResponseEntity vì response stream đã mở)
            e.printStackTrace();
        }
    }

    // === 3. API ĐỔI TÊN FILE ===
    @PostMapping("/media/rename")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> renameFile(@RequestParam("fileId") String fileId,
            @RequestParam("newName") String newName,
            @RequestParam(value = "renamePhysical", defaultValue = "false") boolean renamePhysical) {
        try {
            FileNode fileNode = fileNodeRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("File not found"));

            // Kiểm tra phần mở rộng (Extension) để tránh user xóa mất đuôi file
            String oldName = fileNode.getName();
            String extension = "";
            if (oldName.contains(".")) {
                extension = oldName.substring(oldName.lastIndexOf("."));
            }

            // Nếu tên mới không có đuôi, tự động thêm đuôi cũ vào
            String finalName = newName;
            if (!newName.toLowerCase().endsWith(extension.toLowerCase())) {
                finalName += extension;
            }

            // 1. Đổi tên vật lý (Nếu được yêu cầu)
            if (renamePhysical) {
                StorageVolume vol = storageVolumeService.getVolumeById(fileNode.getVolumeId());
                if (vol != null) {
                    Path oldPath = Paths.get(vol.getMountPoint(), fileNode.getRelativePath());
                    Path newPath = oldPath.resolveSibling(finalName); // Cùng thư mục cha, tên mới

                    if (Files.exists(newPath)) {
                        return ResponseEntity.badRequest().body("File name already exists on disk!");
                    }

                    // Thực hiện rename trên đĩa
                    Files.move(oldPath, newPath);

                    // Cập nhật Relative Path trong DB
                    // relativePath cũ: \Folder\OldName.mp4 -> lấy parent folder
                    String parentRelPath = fileNode.getRelativePath().substring(0,
                            fileNode.getRelativePath().lastIndexOf(File.separator));
                    // Handle trường hợp file ở root
                    if (fileNode.getRelativePath().lastIndexOf(File.separator) == -1) {
                        parentRelPath = "";
                    }

                    String separator = File.separator; // hoặc dùng "\\" hoặc "/" tùy OS, tốt nhất dùng hằng số chuẩn
                    if (!parentRelPath.endsWith(separator) && !parentRelPath.isEmpty()) {
                        parentRelPath += separator;
                    }

                    // Fix path string logic đơn giản hơn: Replace tên cuối
                    String newRelPath = fileNode.getRelativePath().replace(oldName, finalName);
                    fileNode.setRelativePath(newRelPath);
                }
            }

            fileNode.setName(finalName);
            fileNodeRepository.save(fileNode);

            return ResponseEntity.ok(finalName);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    // === 4. UNMOUNT FOLDER (KÈM OPTION XÓA VẬT LÝ) ===
    @PostMapping("/folder/unmount")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> unmountFolder(@RequestParam("mappingId") Integer mappingId,
            @RequestParam(value = "deletePhysical", defaultValue = "false") boolean deletePhysical) {
        try {
            SubjectFolderMapping mapping = mappingRepository.findById(mappingId)
                    .orElseThrow(() -> new IllegalArgumentException("Mapping not found"));

            // 1. Lấy danh sách file trong mapping này
            List<FileNode> files = fileNodeRepository.findBySubjectMappingId(mappingId); // Cần thêm hàm này vào Repo

            // 2. Xóa File Vật lý (Nếu user yêu cầu)
            if (deletePhysical) {
                StorageVolume vol = mapping.getVolume();
                if (vol != null) {
                    try {
                        Path physicalPath = Paths.get(vol.getMountPoint(), mapping.getRelativePath());
                        // Dùng hàm deleteRecursive để xóa cả thư mục
                        deleteDirectoryRecursively(physicalPath);
                    } catch (Exception e) {
                        e.printStackTrace(); // Log lỗi nhưng vẫn tiếp tục xóa DB
                    }
                }
            }

            // 3. Luôn xóa Thumbnail rác (Dù có xóa vật lý hay không, file trong DB cũng sẽ
            // mất -> thumb thành rác)
            for (FileNode file : files) {
                try {
                    Path thumbDir = Paths.get(rootUploadDir, ".cache", "thumbnails");
                    Files.deleteIfExists(thumbDir.resolve(file.getId() + "_small.jpg"));
                    Files.deleteIfExists(thumbDir.resolve(file.getId() + "_medium.jpg"));
                } catch (Exception e) {
                    // ignore
                }
            }

            // 3. Xóa Dữ liệu DB
            // Xóa hết FileNode (Cascade: FileSubject, FileTag, MediaMetadata)
            fileNodeRepository.deleteAllInBatch(files);

            // Xóa Mapping
            mappingRepository.delete(mapping);

            return ResponseEntity.ok("Unmounted successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    private void deleteDirectoryRecursively(Path path) throws java.io.IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
