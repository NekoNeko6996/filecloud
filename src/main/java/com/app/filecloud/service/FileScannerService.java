package com.app.filecloud.service;

import com.app.filecloud.dto.ScanResultDto;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FileScannerService {

    private static final int PREVIEW_LIMIT = 2000;

    public List<ScanResultDto> scanPreview(String rootPathStr) throws IOException {
        Path rootPath = Paths.get(rootPathStr);
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("Đường dẫn gốc không hợp lệ!");
        }

        List<ScanResultDto> results = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.limit(PREVIEW_LIMIT).forEach(path -> {
                if (path.equals(rootPath)) {
                    return;
                }
                if (Files.isDirectory(path)) {
                    return; // Chỉ lấy File
                }
                if (isMediaFile(path)) {
                    results.add(analyzePath(path));
                }
            });
        }
        return results;
    }

    private boolean isMediaFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        // Kiểm tra đuôi file ảnh hoặc video
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
                || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") || name.endsWith(".avi");
    }

    private boolean isImage(String fileName) {
        String name = fileName.toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp");
    }

    private ScanResultDto analyzePath(Path current) {
        ScanResultDto dto = new ScanResultDto();
        dto.setFileName(current.getFileName().toString());
        dto.setAbsolutePath(current.toString());
        dto.setType("FILE");
        try {
            dto.setSize(Files.size(current));
        } catch (IOException e) {
            dto.setSize(0);
        }

        // --- LOGIC TRÍCH XUẤT NGƯỢC (Bottom-up) ---
        Path parent = current.getParent(); // Thư mục chứa file

        if (parent != null) {
            String parentName = parent.getFileName().toString();

            if (isImage(dto.getFileName())) {
                // LOGIC ẢNH: .../[Subject]/[Album]/File.jpg
                dto.setDetectedAlbum(parentName); // Parent là Album

                Path grandparent = parent.getParent();
                if (grandparent != null) {
                    dto.setDetectedSubject(grandparent.getFileName().toString()); // Grandparent là Subject
                }
            } else {
                // LOGIC VIDEO: .../[Subject]/File.mp4
                dto.setDetectedSubject(parentName); // Parent là Subject
                dto.setDetectedAlbum(null); // Video không có Album
            }
        }

        return dto;
    }

    // Helper kiểm tra tên thư mục (Hỗ trợ cả có ngoặc [] và không)
    private boolean isVideoCategory(String name) {
        String clean = name.replace("[", "").replace("]", "").toLowerCase();
        return clean.contains("video");
    }

    private boolean isImageCategory(String name) {
        String clean = name.replace("[", "").replace("]", "").toLowerCase();
        return clean.contains("img") || clean.contains("image") || clean.contains("photo");
    }
}
