package com.app.filecloud.controller.admin;

import com.app.filecloud.dto.GhostDeleteRequest;
import com.app.filecloud.dto.GhostScanResultDto;
import com.app.filecloud.service.DriveCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/drive-check")
@RequiredArgsConstructor
public class DriveCheckController {

    private final DriveCheckService driveCheckService;

    @GetMapping
    public String driveCheckPage(Model model) {
        model.addAttribute("volumes", driveCheckService.getAllVolumes());
        return "admin/drive_check";
    }

    @GetMapping("/api/scan")
    @ResponseBody
    public ResponseEntity<GhostScanResultDto> scanGhostFiles(@RequestParam("volumeId") Integer volumeId) {
        try {
            GhostScanResultDto result = driveCheckService.scanGhostFiles(volumeId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/api/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteGhostItems(@RequestBody GhostDeleteRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            int deletedCount = driveCheckService.deleteGhostItems(request.getItems());
            response.put("success", true);
            response.put("deletedCount", deletedCount);
            response.put("message", "Purged " + deletedCount + " ghost item(s) successfully.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to delete ghost items: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
