package com.app.filecloud.controller.admin;

import com.app.filecloud.dto.SubjectScanResult;
import com.app.filecloud.service.MediaScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/scan-subjects")
@RequiredArgsConstructor
public class SubjectScanController {

    private final MediaScanService mediaScanService;

    // Trang giao diện
    @GetMapping
    public String scanPage() {
        return "admin/scan_subjects";
    }

    // API Preview: Nhận path -> Trả về list Subject tìm thấy
    @PostMapping("/preview")
    @ResponseBody
    public ResponseEntity<List<SubjectScanResult>> preview(@RequestBody Map<String, String> payload) {
        try {
            String path = payload.get("path");
            return ResponseEntity.ok(mediaScanService.scanDirectory(path));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
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
}