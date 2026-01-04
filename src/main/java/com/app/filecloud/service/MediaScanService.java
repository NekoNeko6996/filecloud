package com.app.filecloud.service;

import com.app.filecloud.dto.DuplicateFileGroup;
import com.app.filecloud.dto.ImportResolution;
import com.app.filecloud.dto.ScanProgressDto;
import com.app.filecloud.dto.SubjectScanResult;
import com.app.filecloud.entity.*;
import com.app.filecloud.repository.*;
import java.io.FileInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    
    private final ScanProgressService progressService;
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
                        
                        String hash = calculateFileHash(file);

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
                                .fileHash(hash)
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
    
    public List<DuplicateFileGroup> checkDuplicates(List<String> paths) {
        log.info("--- START CHECK DUPLICATES ---");
        log.info("Received the {} path to be checked.", paths.size());
        
        String userId = getCurrentUserId();
        List<DuplicateFileGroup> results = new ArrayList<>();
        
        for (String pathStr : paths) {
            log.info(">> Scanning Folder: {}", pathStr);
            
            Path folderPath = Paths.get(pathStr);
            if (!Files.exists(folderPath)) {
                log.warn("   [SKIP] The path does not exist.: {}", pathStr);
                continue;
            }
            
            String folderName = folderPath.getFileName().toString();
            List<String> names = parseNames(folderName);
            if (names.isEmpty()) {
                log.warn("   [SKIP] Unable to parse Subject name from: {}", folderName);
                continue;
            }
            
            String mainName = names.get(0);
            log.info("   Subject MainName parse: '{}'", mainName);
            
            Optional<ContentSubject> subjectOpt = subjectRepository.findByMainName(mainName);
            
            if (subjectOpt.isEmpty()) {
                // ĐIỂM MÙ SỐ 1: Subject chưa có trong DB (Import mới) -> Bỏ qua check trùng
                log.warn("   [SKIP] Subject '{}' does not exist in the database -> No duplicates allowed (New Import Logic).", mainName);
                continue;
            }
            
            ContentSubject subject = subjectOpt.get();
            log.info("   Subject ID found: {}", subject.getId());
            
            List<DuplicateFileGroup.DuplicatePair> pairs = new ArrayList<>();
            
            try (Stream<Path> stream = Files.list(folderPath)) {
                List<Path> importFiles = stream.filter(Files::isRegularFile).filter(p -> isVideoFile(p.toString())).toList();
                int totalFiles = importFiles.size();
                log.info("   The {} video file was found in the import folder.", totalFiles);
                
                for (int i = 0; i < totalFiles; i++) {
                    Path importFile = importFiles.get(i);
                    long size = Files.size(importFile);
                    String fileName = importFile.getFileName().toString();
                    
                    reportProgress(userId, mainName, fileName, "Scanning", 0, i + 1, totalFiles);

                    // Tìm các file trong DB có cùng Subject và cùng Kích thước
                    List<FileNode> candidates = fileNodeRepository.findBySubjectIdAndSize(subject.getId(), size);
                    
                    if (candidates.isEmpty()) {
                        // File này kích thước độc nhất -> Chắc chắn không trùng -> Bỏ qua Hash cho nhanh
                        // log.debug("      File '{}' ({} bytes) không có candidate cùng size -> OK.", fileName, size);
                    } else {
                        log.info("      File '{}' ({} bytes) has {} candidate of the same size -> Start Hash...", fileName, size, candidates.size());
                        
                        String importHash = calculateFileHashWithProgress(importFile, userId, mainName, i + 1, totalFiles);
                        
                        for (FileNode existing : candidates) {
                            String existingHash = ensureFileHash(existing);
                            
                            log.debug("         Compare Hash: Import={} vs Existing={}", importHash, existingHash);
                            
                            if (importHash.equals(existingHash)) {
                                log.warn("         !!! DUPLICATE DETECTED !!! With file: {}", existing.getName());
                                
                                pairs.add(DuplicateFileGroup.DuplicatePair.builder()
                                        .existingFileId(existing.getId())
                                        .existingFileName(existing.getName())
                                        .existingPath(existing.getRelativePath())
                                        .existingSize(existing.getReadableSize())
                                        .newFilePath(importFile.toAbsolutePath().toString())
                                        .newFileName(importFile.getFileName().toString())
                                        .newSize(formatSize(size))
                                        .hash(importHash)
                                        .build());
                                break; // Tìm thấy 1 bản trùng là đủ
                            }
                        }
                    }
                    reportProgress(userId, mainName, fileName, "Done", 100, i + 1, totalFiles);
                }
            } catch (IOException e) {
                log.error("Folder reading error " + pathStr, e);
            }
            
            if (!pairs.isEmpty()) {
                results.add(DuplicateFileGroup.builder()
                        .subjectName(mainName)
                        .pairs(pairs)
                        .build());
            } else {
                log.info("   No duplicate files were found in this folder.");
            }
        }
        
        log.info("--- END OF CHECK --- Total number of duplicate groups: {}", results.size());
        return results;
    }
    
    public void executeResolvedImport(ImportResolution resolution) {
        String userId = getCurrentUserId();
        if (userId == null) {
            userId = "c410bace-3f61-43a2-b92e-81e2d6748f8e"; // Cần xử lý kỹ hơn
        }
        // 1. Xử lý các file có xung đột theo lựa chọn user
        if (resolution.getActions() != null) {
            for (ImportResolution.FileAction action : resolution.getActions()) {
                try {
                    Path newFile = Paths.get(action.getNewFilePath());
                    
                    switch (action.getAction()) {
                        case "KEEP_OLD" -> {
                            // Giữ file cũ -> Xóa file đang import
                            log.info("KEEP_OLD: Deleting " + newFile);
                            Files.deleteIfExists(newFile);
                        }
                        
                        case "KEEP_NEW" -> {
                            // Giữ file mới -> Xóa file cũ trong DB và ổ cứng
                            FileNode existingNode = fileNodeRepository.findById(action.getExistingFileId()).orElse(null);
                            if (existingNode != null) {
                                // Xóa vật lý file cũ (cần tìm đường dẫn tuyệt đối từ Volume)
                                StorageVolume vol = storageVolumeService.getVolumeById(existingNode.getVolumeId()); // Cần thêm hàm này
                                if (vol != null) {
                                    Path oldPhysicalPath = Paths.get(vol.getMountPoint(), existingNode.getRelativePath());
                                    log.info("KEEP_NEW: Deleting old file " + oldPhysicalPath);
                                    Files.deleteIfExists(oldPhysicalPath);
                                }
                                // Xóa DB (Cascade sẽ xóa metadata, thumbnails...)
                                fileNodeRepository.delete(existingNode);
                            }
                            // File mới sẽ được import ở bước tiếp theo (nằm trong thư mục safePaths hoặc cần xử lý riêng)
                            // Tuy nhiên, logic import quét theo Folder. 
                            // Nếu file mới vẫn nằm trong folder import, nó sẽ được import tự động ở bước 2.
                        }
                        
                        case "KEEP_BOTH" -> {
                        }
                    }
                    // Không làm gì cả, file mới sẽ được import bình thường
                } catch (Exception e) {
                    log.error("Error executing action " + action.getAction(), e);
                }
            }
        }

        // 2. Tiến hành Import bình thường cho các folder (File đã xóa sẽ tự động bị bỏ qua)
        // Chúng ta tái sử dụng hàm importWithMapping cũ
        // Lưu ý: resolution.safePaths cần chứa tất cả các folder path đã chọn ban đầu
        importWithMapping(resolution.getSafePaths());
    }
    
    private String calculateFileHash(Path path) {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            MessageDigest digest = MessageDigest.getInstance("MD5"); // Hoặc SHA-256
            byte[] byteArray = new byte[1024];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Hash error", e);
            return "ERROR_HASH_" + System.currentTimeMillis();
        }
    }
    
    private String calculateFileHashWithProgress(Path path, String userId, String subjectName, int currentIdx, int totalFiles) {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            long fileSize = Files.size(path);
            long totalRead = 0;
            long lastReportTime = 0;
            
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] byteArray = new byte[8192]; // Tăng buffer lên 8KB cho nhanh
            int bytesCount;
            
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
                totalRead += bytesCount;

                // Chỉ gửi update mỗi 1 giây (1000ms) để tránh làm sập Frontend
                long now = System.currentTimeMillis();
                if (now - lastReportTime > 1000) {
                    int percent = (int) ((totalRead * 100) / fileSize);
                    reportProgress(userId, subjectName, path.getFileName().toString(), "Hashing", percent, currentIdx, totalFiles);
                    lastReportTime = now;
                }
            }

            // Gửi 100% khi xong
            reportProgress(userId, subjectName, path.getFileName().toString(), "Hashing Completed", 100, currentIdx, totalFiles);
            
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Hash error", e);
            return "ERROR_HASH_" + System.currentTimeMillis();
        }
    }
    
    private void reportProgress(String userId, String subject, String fileName, String status, int percent, int current, int total) {
        try {
            if (userId == null) {
                return;
            }
            progressService.sendProgress(userId, ScanProgressDto.builder()
                    .subject(subject)
                    .fileName(fileName)
                    .status(status)
                    .filePercent(percent)
                    .currentFileIndex(current)
                    .totalFiles(total)
                    .build());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            log.error("Lỗi Hàm SEND PROGRESS!!");
        }
    }
    
    private String ensureFileHash(FileNode fileNode) {
        if (fileNode.getFileHash() != null && !fileNode.getFileHash().isEmpty()) {
            return fileNode.getFileHash();
        }
        StorageVolume vol = storageVolumeService.getVolumeById(fileNode.getVolumeId());
        if (vol != null) {
            Path path = Paths.get(vol.getMountPoint(), fileNode.getRelativePath());
            if (Files.exists(path)) {
                String hash = calculateFileHash(path); // Dùng bản không progress cho nhanh gọn
                fileNode.setFileHash(hash);
                fileNodeRepository.save(fileNode);
                return hash;
            }
        }
        return "MISSING_FILE";
    }
    
    private String formatSize(long size) {
        if (size <= 0) {
            return "0 MB";
        }
        double sizeInMb = size / 1048576.0;
        return new DecimalFormat("#.##").format(sizeInMb) + " MB";
    }
}
