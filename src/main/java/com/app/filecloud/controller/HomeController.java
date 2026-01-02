package com.app.filecloud.controller;

import com.app.filecloud.entity.FileNode;
import com.app.filecloud.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final FileStorageService fileStorageService;

    // Trang chủ Dashboard (có hỗ trợ folderId)
    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String folderId, Model model) {
        // 1. Lấy danh sách file/folder
        List<FileNode> items = fileStorageService.getFolderContent(folderId);
        
        // 2. Lấy thông tin folder hiện tại (để hiển thị tên trên header)
        FileNode currentFolder = fileStorageService.getFileNode(folderId);

        model.addAttribute("items", items);
        model.addAttribute("currentFolder", currentFolder);
        model.addAttribute("currentFolderId", folderId); // Để dùng trong form upload/create folder
        
        return "dashboard";
    }

    // Xử lý tạo Folder mới
    @PostMapping("/folder/create")
    public String createFolder(@RequestParam String name, 
                               @RequestParam(required = false) String parentId,
                               @AuthenticationPrincipal UserDetails userDetails) {
        fileStorageService.createFolder(name, parentId, userDetails.getUsername());
        return "redirect:/?folderId=" + (parentId != null ? parentId : "");
    }

    // Xử lý Upload File
    @PostMapping("/file/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam(required = false) String parentId,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        try {
            fileStorageService.uploadFile(file, parentId, userDetails.getUsername());
            redirectAttributes.addFlashAttribute("message", "Upload thành công!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi upload: " + e.getMessage());
        }
        return "redirect:/?folderId=" + (parentId != null ? parentId : "");
    }
}