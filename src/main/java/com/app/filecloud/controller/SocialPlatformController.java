package com.app.filecloud.controller;

import com.app.filecloud.entity.SocialPlatform;
import com.app.filecloud.entity.SubjectSocialLink;
import com.app.filecloud.repository.SocialPlatformRepository;
import com.app.filecloud.repository.SubjectSocialLinkRepository;
import com.app.filecloud.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;

@Controller
@RequestMapping("/admin/platforms")
@RequiredArgsConstructor
public class SocialPlatformController {

    private final SocialPlatformRepository platformRepository;
    private final SubjectSocialLinkRepository linkRepository;
    private final MediaService mediaService;

    // ... imports (thêm Sort, ArrayList...)
    @GetMapping
    public String platformsPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "name_asc") String sort,
            Model model) {

        // 1. Xác định chiều Sắp xếp
        Sort sortOption = switch (sort) {
            case "name_desc" ->
                Sort.by(Sort.Direction.DESC, "name");
            default ->
                Sort.by(Sort.Direction.ASC, "name");
        };

        // 2. Lấy dữ liệu (Có tìm kiếm hoặc không)
        List<SocialPlatform> platforms;
        if (keyword != null && !keyword.isBlank()) {
            platforms = platformRepository.findByNameContainingIgnoreCase(keyword, sortOption);
        } else {
            platforms = platformRepository.findAll(sortOption);
        }
        
        platforms.forEach(p -> {
            p.setUsageCount(linkRepository.countByPlatformId(p.getId()));
        });

        model.addAttribute("platforms", platforms);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentSort", sort); // Để giữ trạng thái dropdown

        return "admin/platforms";
    }

    // API lấy chi tiết để hiện ở Side Panel
    @GetMapping("/{id}/details")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDetails(@PathVariable Integer id) {
        SocialPlatform platform = platformRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Platform not found"));

        // Lấy danh sách Subject đang dùng platform này
        List<SubjectSocialLink> links = linkRepository.findByPlatformId(id);

        List<Map<String, String>> subjects = links.stream().map(link -> {
            Map<String, String> map = new HashMap<>();
            map.put("subjectName", link.getSubject().getMainName());
            map.put("subjectId", link.getSubject().getId().toString());
            map.put("profilePath", link.getProfilePath());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("id", platform.getId());
        response.put("name", platform.getName());
        response.put("baseUrl", platform.getBaseUrl());
        response.put("iconUrl", platform.getIconUrl());
        response.put("linkedSubjects", subjects);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/save")
    public String savePlatform(@RequestParam(value = "id", required = false) Integer id,
            @RequestParam("name") String name,
            @RequestParam("baseUrl") String baseUrl,
            @RequestParam(value = "iconUrl", required = false) String iconUrl,
            @RequestParam(value = "iconFile", required = false) MultipartFile iconFile) {

        SocialPlatform platform;
        if (id != null) {
            platform = platformRepository.findById(id).orElse(new SocialPlatform());
        } else {
            platform = new SocialPlatform();
            platform.setAcvite(true);
        }

        platform.setName(name);
        platform.setBaseUrl(baseUrl);

        // Xử lý Icon: Ưu tiên File -> URL -> Giữ nguyên cũ
        if (iconFile != null && !iconFile.isEmpty()) {
            String path = mediaService.saveAvatar(iconFile); // Tái sử dụng hàm saveAvatar
            platform.setIconUrl(path);
        } else if (iconUrl != null && !iconUrl.isEmpty()) {
            platform.setIconUrl(iconUrl);
        }

        platformRepository.save(platform);
        return "redirect:/admin/platforms";
    }

    @PostMapping("/delete")
    public String deletePlatform(@RequestParam("id") Integer id) {
        // Do DB đã có ON DELETE CASCADE ở bảng subject_social_links nên xóa platform sẽ tự xóa link liên quan
        platformRepository.deleteById(id);
        return "redirect:/admin/platforms";
    }
}
