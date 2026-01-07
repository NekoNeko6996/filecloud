package com.app.filecloud.controller;

import com.app.filecloud.entity.FileNode;
import com.app.filecloud.entity.FileThumbnail;
import com.app.filecloud.entity.MediaMetadata;
import com.app.filecloud.repository.FileNodeRepository;
import com.app.filecloud.repository.FileThumbnailRepository;
import com.app.filecloud.repository.MediaMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileResourceController {

    private final FileThumbnailRepository thumbnailRepository;
    private final MediaMetadataRepository metadataRepository;
    private final FileNodeRepository fileNodeRepository;

    @Value("${app.storage.root:uploads}")
    private String rootUploadDir;

    // 1. API lấy ảnh Thumbnail
    @GetMapping("/thumbnail/{fileId}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable String fileId) {
        try {
            // Tìm thumbnail loại SMALL hoặc MEDIUM
            FileThumbnail thumb = thumbnailRepository.findByFileIdAndType(fileId, FileThumbnail.ThumbType.SMALL)
                    .orElse(null);

            if (thumb == null) {
                return ResponseEntity.notFound().build();
            }

            // Đường dẫn: rootUploadDir + storagePath (đã lưu tương đối trong DB)
            Path filePath = Paths.get(rootUploadDir).resolve(thumb.getStoragePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return ResponseEntity.notFound().build();
    }

    // 2. API lấy Metadata chi tiết (JSON)
    @GetMapping("/metadata/{fileId}")
    public ResponseEntity<?> getFileDetails(@PathVariable String fileId) {
        FileNode node = fileNodeRepository.findById(fileId).orElseThrow();
        MediaMetadata meta = metadataRepository.findById(fileId).orElse(null);

        // Trả về một Map tổng hợp để Frontend dễ hiển thị
        return ResponseEntity.ok(Map.of(
                "name", node.getName(),
                "size", node.getReadableSize(), // Sử dụng hàm helper đã tạo ở bước trước
                "type", node.getMimeType() != null ? node.getMimeType() : "Unknown",
                "created", node.getCreatedAt().toString(),
                "meta", meta != null ? meta : "N/A",
                "relative", node.getRelativePath(),
                "hash", node.getFileHash()
        ));
    }
}
