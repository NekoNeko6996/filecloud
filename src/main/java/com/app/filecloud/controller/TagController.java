package com.app.filecloud.controller;

import com.app.filecloud.entity.FileNode;
import com.app.filecloud.entity.Tag;
import com.app.filecloud.repository.FileNodeRepository;
import com.app.filecloud.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagRepository tagRepository;
    private final FileNodeRepository fileNodeRepository;

    @GetMapping
    public String tagsPage(Model model) {
        model.addAttribute("tags", tagRepository.findAll());
        return "admin/tags";
    }

    // API lấy chi tiết Tag và danh sách File liên quan
    @GetMapping("/{id}/details")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTagDetails(@PathVariable Integer id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found"));

        // Lấy danh sách file chứa tag này
        List<FileNode> files = fileNodeRepository.findByTagId(id);

        List<Map<String, Object>> fileDtos = files.stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", f.getId());
            m.put("name", f.getName());
            m.put("isVideo", f.isVideo());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("tag", tag);
        response.put("files", fileDtos);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/save")
    public String saveTag(@RequestParam(value = "id", required = false) Integer id,
            @RequestParam("name") String name,
            @RequestParam(value = "slug", required = false) String slug,
            @RequestParam(value = "colorHex", required = false) String colorHex,
            @RequestParam(value = "description", required = false) String description) {

        Tag tag;
        if (id != null) {
            tag = tagRepository.findById(id).orElse(new Tag());
        } else {
            tag = new Tag();
        }

        tag.setName(name);

        // Auto generate slug nếu trống
        if (slug == null || slug.trim().isEmpty()) {
            slug = toSlug(name);
        }
        tag.setSlug(slug);

        tag.setColorHex(colorHex);
        tag.setDescription(description);

        tagRepository.save(tag);
        return "redirect:/admin/tags";
    }

    @PostMapping("/delete")
    public String deleteTag(@RequestParam("id") Integer id) {
        // DB đã có ON DELETE CASCADE ở bảng file_tags nên chỉ cần xóa Tag là sạch
        tagRepository.deleteById(id);
        return "redirect:/admin/tags";
    }

    // Helper tạo slug
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    private String toSlug(String input) {
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }
}
