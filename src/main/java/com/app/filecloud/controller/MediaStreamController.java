package com.app.filecloud.controller;

import com.app.filecloud.entity.FileNode;
import com.app.filecloud.entity.FileThumbnail;
import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.repository.FileNodeRepository;
import com.app.filecloud.repository.FileThumbnailRepository;
import com.app.filecloud.repository.StorageVolumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaStreamController {

    private final FileThumbnailRepository thumbnailRepository;
    private final FileNodeRepository fileNodeRepository;
    private final StorageVolumeRepository volumeRepository;

    @Value("${app.storage.root:uploads}")
    private String rootUploadDir;

    // 1. API Lấy Thumbnail
    @GetMapping("/thumbnail/{fileId}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable String fileId) {
        // Ưu tiên lấy ảnh MEDIUM, nếu không có thì lấy SMALL
        FileThumbnail thumb = thumbnailRepository.findByFileIdAndType(fileId, FileThumbnail.ThumbType.MEDIUM)
                .or(() -> thumbnailRepository.findByFileIdAndType(fileId, FileThumbnail.ThumbType.SMALL))
                .orElse(null);

        if (thumb != null) {
            // FIX LỖI 404 THUMBNAIL: Ghép root upload dir vào
            Path path = Paths.get(rootUploadDir, thumb.getStoragePath());

            if (Files.exists(path)) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(new FileSystemResource(path));
            }
        }
        return ResponseEntity.notFound().build();
    }

    // 2. API Stream Video (Hỗ trợ tua - Range Request)
    @GetMapping("/stream/{fileId}")
    public ResponseEntity<Resource> streamVideo(@PathVariable String fileId,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        FileNode fileNode = fileNodeRepository.findById(fileId).orElse(null);

        if (fileNode == null || fileNode.getRelativePath() == null || fileNode.getVolumeId() == null) {
            return ResponseEntity.notFound().build();
        }

        // FIX LỖI 404 VIDEO: Ghép MountPoint ổ cứng + Relative Path
        StorageVolume volume = volumeRepository.findById(fileNode.getVolumeId()).orElse(null);
        if (volume == null) {
            return ResponseEntity.notFound().build();
        }

        // Logic ghép đường dẫn: F:\ + \TESTFOLDER\video.mp4 -> F:\TESTFOLDER\video.mp4
        // Cần xử lý dấu \ hoặc / để tránh bị thừa
        String mountPoint = volume.getMountPoint(); // VD: F:\
        String relPath = fileNode.getRelativePath(); // VD: \VIDEO\Abc.mp4

        // Xóa dấu \ ở cuối mountPoint nếu có
        if (mountPoint.endsWith("\\") || mountPoint.endsWith("/")) {
            mountPoint = mountPoint.substring(0, mountPoint.length() - 1);
        }
        // Đảm bảo relPath bắt đầu bằng \
        if (!relPath.startsWith("\\") && !relPath.startsWith("/")) {
            relPath = File.separator + relPath;
        }

        Path videoPath = Paths.get(mountPoint + relPath);

        if (!Files.exists(videoPath)) {
            return ResponseEntity.notFound().build();
        }

        // Trả về Resource hỗ trợ Range (Tua video)
        Resource videoResource = new FileSystemResource(videoPath);
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaTypeFactory.getMediaType(videoPath.getFileName().toString())
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(videoResource);
    }

    static class MediaTypeFactory {

        public static java.util.Optional<MediaType> getMediaType(String fileName) {
            String name = fileName.toLowerCase();
            if (name.endsWith(".mp4")) {
                return java.util.Optional.of(MediaType.valueOf("video/mp4"));
            }
            if (name.endsWith(".mkv")) {
                return java.util.Optional.of(MediaType.valueOf("video/x-matroska"));
            }
            if (name.endsWith(".webm")) {
                return java.util.Optional.of(MediaType.valueOf("video/webm"));
            }
            return java.util.Optional.empty();
        }
    }
}
