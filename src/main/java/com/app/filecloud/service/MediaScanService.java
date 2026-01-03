package com.app.filecloud.service;

import com.app.filecloud.dto.SubjectScanResult;
import com.app.filecloud.entity.*;
import com.app.filecloud.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaScanService {

    private final ContentSubjectRepository subjectRepository;
    private final FileNodeRepository fileNodeRepository;
    private final FileSubjectsRepository fileSubjectsRepository; // Repository cho bảng trung gian
    private final UserRepository userRepository;
    
    private final MediaService mediaService;

    private final StorageVolumeService storageVolumeService;
    private final SubjectFolderMappingRepository mappingRepository;

    // Regex để bắt nội dung trong ngoặc vuông: [Name] -> Name
    private final Pattern BRACKET_PATTERN = Pattern.compile("\\[(.*?)\\]");

    private final ApplicationContext applicationContext;

    // 1. HÀM QUÉT PREVIEW
    public List<SubjectScanResult> scanDirectory(String rootPathStr) throws IOException {
        List<SubjectScanResult> results = new ArrayList<>();
        Path rootPath = Paths.get(rootPathStr);

        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("Đường dẫn không hợp lệ!");
        }

        try (Stream<Path> stream = Files.list(rootPath)) {
            stream.filter(Files::isDirectory).forEach(folder -> {
                String folderName = folder.getFileName().toString();

                // Phân tích tên: [Name][Alias]
                List<String> names = parseNames(folderName);

                if (!names.isEmpty()) {
                    String mainName = names.get(0);
                    List<String> aliases = names.subList(1, names.size());

                    // Đếm file video bên trong
                    int count = countMediaFiles(folder);

                    // Kiểm tra DB xem Subject tồn tại chưa
                    boolean exists = subjectRepository.findByMainName(mainName).isPresent();

                    results.add(SubjectScanResult.builder()
                            .folderName(folderName)
                            .fullPath(folder.toAbsolutePath().toString())
                            .parsedMainName(mainName)
                            .parsedAliases(aliases)
                            .mediaCount(count)
                            .existsInDb(exists)
                            .build());
                }
            });
        }
        return results;
    }

    public void importWithMapping(List<String> selectedPaths) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            currentUserId = userRepository.findAll().stream()
                    .findFirst()
                    .map(u -> u.getId().toString())
                    .orElseThrow(() -> new RuntimeException("Lỗi: Database chưa có User nào!"));
        }
        MediaScanService proxy = applicationContext.getBean(MediaScanService.class);

        for (String pathStr : selectedPaths) {
            try {
                // GỌI QUA PROXY -> Transaction sẽ hoạt động
                proxy.processPathWithMapping(pathStr, currentUserId);
            } catch (Exception e) {
                log.error("Lỗi xử lý thư mục {}: {}", pathStr, e.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processPathWithMapping(String pathStr, String userId) {
        Path folderPath = Paths.get(pathStr);
        if (!Files.exists(folderPath)) {
            return;
        }

        // 1. Lấy Volume
        StorageVolume volume = storageVolumeService.getVolumeFromPath(pathStr);
        if (volume == null) {
            log.error("Không tìm thấy ổ đĩa cho path: {}", pathStr);
            return;
        }

        // 2. Parse tên Subject
        String folderName = folderPath.getFileName().toString();
        List<String> names = parseNames(folderName);
        if (names.isEmpty()) {
            return;
        }
        String mainName = names.get(0);

        // 3. Tìm hoặc tạo Subject
        ContentSubject subject = subjectRepository.findByMainName(mainName)
                .orElseGet(() -> subjectRepository.save(ContentSubject.builder()
                .mainName(mainName)
                .aliasName1(names.size() > 1 ? names.get(1) : null)
                .build()));

        // 4. Tạo Mapping
        String root = folderPath.getRoot().toString(); // VD: F:\
        // Cắt bỏ phần gốc ổ đĩa để lấy relative path chuẩn (VD: \VIDEO\Name)
        String relativePath = pathStr.substring(root.length() - 1);

        SubjectFolderMapping mapping = mappingRepository
                .findBySubjectIdAndVolumeIdAndRelativePath(subject.getId(), volume.getId(), relativePath)
                .orElseGet(() -> mappingRepository.save(SubjectFolderMapping.builder()
                .subject(subject)
                .volume(volume)
                .relativePath(relativePath)
                .build()));

        // 5. Import File (Transaction đang Active ở đây)
        importFilesToMapping(folderPath, subject, mapping, volume, userId);
    }

    private void importFilesToMapping(Path folderPath, ContentSubject subject, SubjectFolderMapping mapping, StorageVolume volume, String userId) {
        try (Stream<Path> stream = Files.list(folderPath)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    String fileName = file.getFileName().toString();
                    if (isVideoFile(fileName)) { // Gọi hàm helper cũ
                        String fileRelPath = mapping.getRelativePath() + "\\" + fileName;

                        if (fileNodeRepository.findByVolumeIdAndRelativePath(volume.getId(), fileRelPath).isPresent()) {
                            log.info("File trùng lặp!");
                            return;
                        }

                        // === TẠO FILE NODE MỚI ===
                        // Nhờ implement Persistable, lệnh save() sẽ là INSERT thẳng
                        FileNode fileNode = FileNode.builder()
                                .id(UUID.randomUUID().toString())
                                .name(fileName)
                                .type(FileNode.Type.FILE) // Giả định bạn có Enum Type
                                .size(Files.size(file))
                                .mimeType("video/mp4")
                                .volumeId(volume.getId())
                                .subjectMappingId(mapping.getId())
                                .relativePath(fileRelPath)
                                .ownerId(userId)
                                .createdAt(LocalDateTime.now())
                                // Quan trọng: set isNew = true (đã mặc định trong Entity)
                                .build();

                        fileNodeRepository.save(fileNode);

                        // Link Subject
                        FileSubject link = new FileSubject();
                        link.setFileId(fileNode.getId());
                        link.setSubjectId(subject.getId());
                        link.setIsMainOwner(true);
                        fileSubjectsRepository.save(link);

                        log.info("Đã map file: " + fileName);
                        
                        // === 2. GỌI XỬ LÝ MEDIA (METADATA & THUMBNAIL) ===
                        // QUAN TRỌNG: Sử dụng TransactionSynchronizationManager để đợi Commit xong mới chạy Async
                        // Nếu gọi trực tiếp mediaService.processMedia() ở đây, luồng Async sẽ chạy trước khi DB có dữ liệu -> Lỗi FK
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    // Lúc này FileNode đã nằm an toàn trong DB, ta có thể tạo Metadata
                                    mediaService.processMedia(fileNode, file);
                                } catch (Exception e) {
                                    log.error("Lỗi xử lý media background: " + fileName, e);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    // Log lỗi file lẻ nhưng không ném exception để không rollback cả folder
                    log.error("Skipping file " + file + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Error reading folder", e);
        }
    }

    // --- HELPER ---
    private List<String> parseNames(String folderName) {
        List<String> matches = new ArrayList<>();
        Matcher m = BRACKET_PATTERN.matcher(folderName);
        while (m.find()) {
            matches.add(m.group(1).trim());
        }
        // Nếu không có ngoặc vuông, lấy nguyên tên folder
        if (matches.isEmpty() && !folderName.startsWith(".")) {
            matches.add(folderName);
        }
        return matches;
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            // Tùy vào cách bạn cài đặt UserDetails, phần này có thể khác
            // Giả sử Principal là username, ta cần query DB để lấy ID
            String username = authentication.getName();
            return userRepository.findByUsername(username)
                    .map(user -> user.getId().toString())
                    .orElse(null);
        }
        return null;
    }

    private int countMediaFiles(Path folder) {
        try (Stream<Path> s = Files.list(folder)) {
            return (int) s.filter(p -> isVideoFile(p.toString())).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private boolean isVideoFile(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov");
    }
}
