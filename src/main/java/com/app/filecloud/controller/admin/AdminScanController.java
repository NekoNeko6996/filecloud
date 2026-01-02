package com.app.filecloud.controller.admin;

import com.app.filecloud.dto.ScanResultDto;
import com.app.filecloud.service.FileScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/scan")
@RequiredArgsConstructor
public class AdminScanController {

    private final FileScannerService fileScannerService;

    // API: GET /api/admin/scan/preview?path=D:/Media
    @GetMapping("/preview")
    public ResponseEntity<?> previewScan(@RequestParam("path") String path) {
        try {
            List<ScanResultDto> results = fileScannerService.scanPreview(path);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}