package com.app.filecloud.controller;

import com.app.filecloud.entity.GalleryAlbum;
import com.app.filecloud.entity.GalleryPhoto;
import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.repository.GalleryAlbumRepository;
import com.app.filecloud.repository.GalleryPhotoRepository;
import com.app.filecloud.repository.StorageVolumeRepository;
import com.app.filecloud.repository.UserRepository;
import com.app.filecloud.service.GalleryService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Controller
@RequestMapping("/gallery")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService galleryService;
    private final UserRepository userRepository;
    private final GalleryPhotoRepository photoRepository;
    private final StorageVolumeRepository volumeRepository;
    private final GalleryAlbumRepository albumRepository;

    /**
     * Helper: Lấy ID của user đang đăng nhập từ SecurityContext
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return userRepository.findByUsername(auth.getName())
                    .map(user -> user.getId().toString()) // Chuyển ID sang String (UUID)
                    .orElse(null);
        }
        return null;
    }

    // 1. Dashboard
    @GetMapping
    public String index(Model model) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return "redirect:/login";
        }

        GalleryAlbum defaultAlbum = galleryService.getOrCreateDefaultAlbum(currentUserId);

        model.addAttribute("albums", galleryService.getUserAlbums(currentUserId));
        model.addAttribute("defaultAlbum", defaultAlbum);
        model.addAttribute("photos", galleryService.getAlbumPhotos(defaultAlbum.getId()));

        return "gallery/index";
    }

    // 2. View Album Details
    @GetMapping("/{id}")
    public String viewAlbum(@PathVariable String id, Model model) {
        GalleryAlbum album = albumRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid album Id:" + id));

        // Lấy ảnh của album đó
        var photos = photoRepository.findByAlbumIdOrderByCreatedAtDesc(id);

        model.addAttribute("album", album);
        model.addAttribute("photos", photos);

        return "gallery/view"; // Đảm bảo trả về file template mới tạo
    }

    // 3. Create Album
    @PostMapping("/create")
    public String createAlbum(@ModelAttribute GalleryAlbum album) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return "redirect:/login";
        }

        album.setUserId(currentUserId);
        galleryService.createAlbum(album);
        return "redirect:/gallery/albums";
    }

    // 4. Upload API (Delegate to Service)
    @PostMapping("/upload-check")
    @ResponseBody
    public ResponseEntity<?> uploadWithDuplicateCheck(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "albumId", required = false) String albumId,
            @RequestParam(value = "forceKeep", defaultValue = "false") boolean forceKeep) {

        try {
            String currentUserId = getCurrentUserId();
            if (currentUserId == null) {
                return ResponseEntity.status(401).body("User not authenticated");
            }

            Map<String, Object> result = galleryService.uploadPhotoWithDuplicateCheck(file, albumId, forceKeep, currentUserId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // 5. Resolve Duplicate Action (Skip/Delete)
    @PostMapping("/resolve-duplicate")
    @ResponseBody
    public ResponseEntity<?> resolveDuplicate(@RequestParam("tempPath") String tempPath, @RequestParam("action") String action) {
        if ("SKIP".equals(action)) {
            boolean deleted = galleryService.deletePhysicalFile(tempPath);
            return deleted ? ResponseEntity.ok("SKIPPED_AND_DELETED") : ResponseEntity.ok("FILE_NOT_FOUND_BUT_SKIPPED");
        }
        return ResponseEntity.badRequest().body("Unknown action");
    }

    // 6. Get Thumbnail
    @GetMapping("/thumb/{photoId}")
    @ResponseBody
    public ResponseEntity<Resource> getThumbnail(@PathVariable String photoId) {
        try {
            Resource resource = galleryService.getThumbnailResource(photoId);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // API: Lấy chi tiết ảnh
    @GetMapping("/api/details/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPhotoDetails(@PathVariable String id) {
        try {
            return ResponseEntity.ok(galleryService.getPhotoDetails(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // API: Xóa ảnh (Batch)
    @PostMapping("/media/delete")
    @ResponseBody
    public ResponseEntity<?> deletePhotos(@RequestBody Map<String, Object> payload) {
        try {
            List<String> ids = (List<String>) payload.get("fileIds");
            boolean deletePhysical = (boolean) payload.get("deletePhysical");

            galleryService.deletePhotos(ids, deletePhysical);
            return ResponseEntity.ok("Deleted successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // API: Đổi tên
    @PostMapping("/media/rename")
    @ResponseBody
    public ResponseEntity<?> renamePhoto(@RequestParam("fileId") String fileId,
            @RequestParam("newName") String newName) {
        try {
            // Mặc định không rename file vật lý vì ta dùng UUID để lưu trữ
            String result = galleryService.renamePhoto(fileId, newName, false);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // API Download File
    @GetMapping("/download/{photoId}")
    public ResponseEntity<Resource> downloadPhoto(@PathVariable String photoId) {
        try {
            GalleryPhoto photo = photoRepository.findById(photoId)
                    .orElseThrow(() -> new RuntimeException("Photo not found"));

            StorageVolume volume = volumeRepository.findById(photo.getVolumeId())
                    .orElseThrow(() -> new RuntimeException("Volume offline"));

            Path volumeRoot = Paths.get(volume.getMountPoint());
            Path filePath = volumeRoot.resolve(photo.getStoragePath());

            if (!Files.exists(filePath)) {
                throw new RuntimeException("File not found on disk");
            }

            Resource resource = new UrlResource(filePath.toUri());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + photo.getOriginalFilename() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 1. Màn hình danh sách Albums (Grid)
    @GetMapping("/albums")
    public String listAlbums(Model model) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return "redirect:/login";
        }

        model.addAttribute("albums", galleryService.getUserAlbums(currentUserId));
        return "gallery/albums";
    }

    // 2. API Xóa Album
    @PostMapping("/albums/delete")
    @ResponseBody
    public ResponseEntity<?> deleteAlbum(@RequestParam("id") String albumId) {
        try {
            // Logic xóa album nên nằm trong Service (xóa cả ảnh con + file vật lý)
            galleryService.deleteAlbum(albumId);
            return ResponseEntity.ok("Deleted");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // 3. API Đổi tên Album (Update)
    @PostMapping("/albums/rename")
    @ResponseBody
    public ResponseEntity<?> renameAlbum(@RequestParam("id") String albumId, @RequestParam("title") String newTitle) {
        try {
            galleryService.renameAlbum(albumId, newTitle);
            return ResponseEntity.ok("Renamed");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
