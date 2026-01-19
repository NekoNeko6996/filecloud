package com.app.filecloud.controller.admin;

import com.app.filecloud.dto.DuplicateFileGroup;
import com.app.filecloud.dto.ImportResolution;
import com.app.filecloud.dto.SubjectScanResult;
import com.app.filecloud.repository.UserRepository;
import com.app.filecloud.service.MediaScanService;
import com.app.filecloud.service.ScanProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
@RequestMapping("/admin/scan-subjects")
@RequiredArgsConstructor
public class SubjectScanController {

    private final MediaScanService mediaScanService;
    private final ScanProgressService progressService;
    private final UserRepository userRepository;

    // Trang giao diện
    @GetMapping
    public String scanPage() {
        return "admin/scan_subjects";
    }

    @PostMapping("/execute")
    @ResponseBody
    public ResponseEntity<String> executeImport(@RequestBody List<String> paths) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        // Chạy Async thủ công bằng Thread mới để trả response ngay cho Client
        new Thread(() -> {
            try {
                mediaScanService.importWithMapping(paths, userId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return ResponseEntity.ok("Import started in background...");
    }

    @PostMapping("/preview")
    @ResponseBody
    public ResponseEntity<List<SubjectScanResult>> preview(@RequestBody Map<String, String> payload) {
        try {
            return ResponseEntity.ok(mediaScanService.scanDirectory(payload.get("path")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/progress")
    public SseEmitter subscribe() {
        String userId = getCurrentUserId();
        if (userId == null) {
            // Nếu chưa login, trả về null hoặc throw lỗi tùy bạn
            return null;
        }
        // Gọi service để tạo và lưu emitter
        return progressService.subscribe(userId);
    }

    @PostMapping("/check-duplicates")
    @ResponseBody
    public ResponseEntity<List<DuplicateFileGroup>> checkDuplicates(@RequestBody List<String> paths) {
        try {
            List<DuplicateFileGroup> dup = mediaScanService.checkDuplicates(paths);
            System.out.println(dup.size());
            return ResponseEntity.ok(dup);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.ok(null);
        }
    }

    @PostMapping("/execute-resolved")
    @ResponseBody
    public ResponseEntity<String> executeResolved(@RequestBody ImportResolution resolution) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        // Chạy Async
        new Thread(() -> {
            try {
                mediaScanService.executeResolvedImport(resolution, userId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return ResponseEntity.ok("Import started in background...");
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
