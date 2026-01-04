package com.app.filecloud.service;

import com.app.filecloud.entity.FileNode;
import com.app.filecloud.entity.FileSubject;
import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.entity.SubjectFolderMapping;
import com.app.filecloud.repository.FileNodeRepository;
import com.app.filecloud.repository.FileSubjectsRepository;
import com.app.filecloud.repository.SubjectFolderMappingRepository;
import com.app.filecloud.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final FileNodeRepository fileNodeRepository;
    private final UserRepository userRepository;
    private final MediaService mediaService;

    private final SubjectFolderMappingRepository mappingRepository;
    private final FileSubjectsRepository fileSubjectsRepository;
    private final StorageVolumeService storageVolumeService;

    @Value("${app.storage.root:uploads}")
    private String rootUploadDir;

    // --- CÁC HÀM CŨ (GIỮ NGUYÊN) ---
    public List<FileNode> getFolderContent(String parentId) {
        if (parentId == null || parentId.isEmpty() || parentId.equals("root")) {
            return fileNodeRepository.findByParentIdIsNullOrderByTypeDescNameAsc();
        }
        return fileNodeRepository.findByParentIdOrderByTypeDescNameAsc(parentId);
    }

    public FileNode getFileNode(String id) {
        return (id == null || id.equals("root")) ? null : fileNodeRepository.findById(id).orElse(null);
    }

    // --- HÀM MỚI: XÂY DỰNG ĐƯỜNG DẪN TỪ ID ---
    // Input: ID của folder con (VD: 123)
    // Output: Chuỗi đường dẫn (VD: "CongViec/DuAnA/TaiLieu")
    private String buildFolderPath(String folderId) {
        if (folderId == null || folderId.isEmpty() || folderId.equals("root")) {
            return "";
        }

        StringBuilder pathBuilder = new StringBuilder();
        FileNode current = fileNodeRepository.findById(folderId).orElse(null);

        // Vòng lặp truy ngược lên cha
        while (current != null) {
            if (!pathBuilder.isEmpty()) {
                pathBuilder.insert(0, File.separator);
            }
            pathBuilder.insert(0, current.getName()); // Lấy tên folder

            if (current.getParentId() == null) {
                break; // Đã đến root
            }
            current = fileNodeRepository.findById(current.getParentId()).orElse(null);
        }
        return pathBuilder.toString();
    }

    // --- CẬP NHẬT: TẠO FOLDER (Tạo cả trên ổ cứng) ---
    public void createFolder(String name, String parentId, String ownerUsername) {
        String ownerId = userRepository.findByUsername(ownerUsername).get().getId().toString();

        // 1. Tính toán đường dẫn vật lý cho folder mới
        String parentPathStr = buildFolderPath(parentId);
        Path fullFolderPath = Paths.get(rootUploadDir, parentPathStr, name);

        // 2. Tạo folder vật lý
        try {
            Files.createDirectories(fullFolderPath);
        } catch (IOException e) {
            log.error("Không thể tạo folder vật lý: " + fullFolderPath, e);
            // Vẫn tiếp tục tạo DB hoặc ném lỗi tùy bạn
        }

        // 3. Lưu DB
        FileNode folder = FileNode.builder()
                .name(name)
                .type(FileNode.Type.FOLDER)
                .parentId((parentId != null && !parentId.isEmpty()) ? parentId : null)
                .ownerId(ownerId)
                .relativePath(Paths.get(parentPathStr, name).toString()) // Lưu đường dẫn tương đối
                .build();

        fileNodeRepository.save(folder);
    }

    // --- CẬP NHẬT: UPLOAD FILE (Lưu đúng cấu trúc) ---
    public void uploadFile(MultipartFile file, String parentId, String ownerUsername) throws IOException {
        String ownerId = userRepository.findByUsername(ownerUsername).get().getId().toString();

        // 1. Xác định thư mục chứa file
        String relativeFolderPath = buildFolderPath(parentId);
        Path targetFolder = Paths.get(rootUploadDir).resolve(relativeFolderPath);

        // Tạo thư mục nếu chưa có (Phòng trường hợp tạo folder DB nhưng xóa tay folder vật lý)
        if (!Files.exists(targetFolder)) {
            Files.createDirectories(targetFolder);
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            originalName = "unknown_file";
        }

        // 2. Xử lý trùng tên file
        // Nếu file "anh.jpg" đã có, đổi thành "anh_TIMESTAMP.jpg" để không ghi đè
        // (Hoặc bạn có thể giữ nguyên nếu muốn ghi đè)
        String storedFileName = originalName;
        Path targetPath = targetFolder.resolve(storedFileName);

        if (Files.exists(targetPath)) {
            String nameWithoutExt = originalName.contains(".") ? originalName.substring(0, originalName.lastIndexOf('.')) : originalName;
            String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
            storedFileName = nameWithoutExt + "_" + System.currentTimeMillis() + ext;
            targetPath = targetFolder.resolve(storedFileName);
        }

        // 3. Lưu file vật lý
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 4. Lưu DB
        FileNode fileNode = FileNode.builder()
                .name(storedFileName) // Tên hiển thị (có thể dùng originalName nếu muốn giữ tên gốc trong DB)
                .type(FileNode.Type.FILE)
                .parentId((parentId != null && !parentId.isEmpty()) ? parentId : null)
                .size(file.getSize())
                .mimeType(file.getContentType())
                .ownerId(ownerId)
                .relativePath(Paths.get(relativeFolderPath, storedFileName).toString()) // Lưu path: "Media/anh.jpg"
                .build();

        fileNodeRepository.save(fileNode);

        FileNode savedNode = fileNodeRepository.save(fileNode);
        mediaService.processMedia(savedNode, targetPath);
    }

    public void uploadFileToMapping(MultipartFile file, Integer mappingId, String userId) throws IOException {
        // 1. Lấy thông tin Mapping
        SubjectFolderMapping mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found"));

        StorageVolume volume = mapping.getVolume();

        // 2. Xây dựng đường dẫn vật lý: Volume MountPoint + Relative Path
        Path folderPath = Paths.get(volume.getMountPoint(), mapping.getRelativePath());
        if (!Files.exists(folderPath)) {
            Files.createDirectories(folderPath);
        }

        // 3. Xử lý tên file (tránh trùng)
        String originalName = file.getOriginalFilename();
        String storedFileName = originalName;
        Path targetPath = folderPath.resolve(storedFileName);

        if (Files.exists(targetPath)) {
            String nameWithoutExt = originalName.contains(".") ? originalName.substring(0, originalName.lastIndexOf('.')) : originalName;
            String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
            storedFileName = nameWithoutExt + "_" + System.currentTimeMillis() + ext;
            targetPath = folderPath.resolve(storedFileName);
        }

        // 4. Lưu file vật lý
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 5. Lưu FileNode vào DB
        // Tính lại relative path chuẩn để lưu DB (Bắt đầu bằng \)
        String dbRelativePath = mapping.getRelativePath() + File.separator + storedFileName;
        if (!dbRelativePath.startsWith(File.separator)) {
            dbRelativePath = File.separator + dbRelativePath;
        }

        FileNode fileNode = FileNode.builder()
                .id(java.util.UUID.randomUUID().toString())
                .name(storedFileName)
                .type(FileNode.Type.FILE)
                .size(file.getSize())
                .mimeType(file.getContentType())
                .volumeId(volume.getId())
                .subjectMappingId(mapping.getId())
                .relativePath(dbRelativePath)
                .ownerId(userId)
                .createdAt(java.time.LocalDateTime.now())
                .isNew(true)
                .build();

        fileNodeRepository.save(fileNode);

        // 6. Link với Subject (Bảng trung gian)
        FileSubject link = new FileSubject();
        link.setFileId(fileNode.getId());
        link.setSubjectId(mapping.getSubject().getId());
        link.setIsMainOwner(true);
        fileSubjectsRepository.save(link);

        // 7. Xử lý Media Async (Thumbnail, Metadata)
        mediaService.processMedia(fileNode, targetPath);
    }
}
