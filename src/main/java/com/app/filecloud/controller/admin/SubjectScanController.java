package com.app.filecloud.controller.admin;

import com.app.filecloud.dto.DuplicateFileGroup;
import com.app.filecloud.dto.ImportResolution;
import com.app.filecloud.dto.SubjectScanResult;
import com.app.filecloud.repository.UserRepository;
import com.app.filecloud.service.MediaScanService;
import com.app.filecloud.service.ScanProgressService;
import static java.lang.Math.log;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.apache.catalina.connector.Response;
import org.springframework.http.MediaType;
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
        try {
            // ĐỔI TỪ importSubjects -> importWithMapping
            mediaScanService.importWithMapping(paths);
            return ResponseEntity.ok("Đã import và map thành công!");
        } catch (Exception e) { // In lỗi ra console server để debug
            // In lỗi ra console server để debug
            return ResponseEntity.internalServerError().body("Lỗi: " + e.getMessage());
        }
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

    @PostMapping("/check-duplicates")
    @ResponseBody
    public ResponseEntity<List<DuplicateFileGroup>> checkDuplicates(@RequestBody List<String> paths) {
        try {
            List<DuplicateFileGroup> dup = mediaScanService.checkDuplicates(paths);
            System.out.println(dup.size());
            return ResponseEntity.ok(dup);
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.ok(null);
        }
    }

    @PostMapping("/execute-resolved")
    @ResponseBody
    public ResponseEntity<String> executeResolved(@RequestBody ImportResolution resolution) {
        try {
            mediaScanService.executeResolvedImport(resolution);
            return ResponseEntity.ok("Import completed successfully!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping(value = "/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress() {
        String userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return progressService.subscribe(userId);
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
