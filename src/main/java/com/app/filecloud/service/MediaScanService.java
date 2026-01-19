package com.app.filecloud.service;

import com.app.filecloud.dto.DuplicateFileGroup;
import com.app.filecloud.dto.ImportResolution;
import com.app.filecloud.dto.ScanProgressDto;
import com.app.filecloud.dto.SubjectScanResult;
import com.app.filecloud.entity.*;
import com.app.filecloud.repository.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
                    List<String> lowerNames = names.stream().map(String::toLowerCase).toList();
                    boolean exists = subjectRepository.findFirstByAnyName(lowerNames).isPresent();

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

    public void importWithMapping(List<String> selectedPaths, String userId) {
        MediaScanService proxy = applicationContext.getBean(MediaScanService.class);
        int folderCounts = selectedPaths.size();
        int currentIndex = 0;

        for (String pathStr : selectedPaths) {
            try {
                // GỌI QUA PROXY -> Transaction sẽ hoạt động
                currentIndex++;
                log.info("------------------------ [START IMPORTING SUBJECT (" + currentIndex + "/" + folderCounts
                        + ")] ------------------------");
                proxy.processPathWithMapping(pathStr, userId);
            } catch (Exception e) {
                log.error("Lỗi xử lý thư mục {}: {}", pathStr, e.getMessage());
            }
        }

        // --- THÊM ĐOẠN NÀY Ở CUỐI CÙNG ---
        try {
            ScanProgressDto doneSignal = ScanProgressDto.builder()
                    .subject("All Done")
                    .fileName("Completed")
                    .status("COMPLETED") // Key để JS nhận diện
                    .filePercent(100)
                    .currentFileIndex(folderCounts)
                    .totalFiles(folderCounts)
                    .build();
            progressService.sendProgress(userId, doneSignal);
        } catch (Exception e) {
            log.warn("Could not send final completion event", e);
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

        List<String> lowerNames = names.stream().map(String::toLowerCase).toList();

        // 3. Tìm hoặc tạo Subject
        ContentSubject subject = subjectRepository.findFirstByAnyName(lowerNames)
                .orElseGet(() -> subjectRepository.save(ContentSubject.builder()
                        .mainName(names.get(0))
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

    private void importFilesToMapping(Path folderPath, ContentSubject subject, SubjectFolderMapping mapping,
            StorageVolume volume, String userId) {
        try {
            // BƯỚC 1: Đếm tổng số file video trước để tính %
            long totalFiles;
            try (Stream<Path> s = Files.list(folderPath)) {
                totalFiles = s.filter(Files::isRegularFile)
                        .filter(p -> isVideoFile(p.toString())) // Reuse hàm check đuôi video
                        .count();
            }

            if (totalFiles == 0) {
                return;
            }

            AtomicInteger currentCount = new AtomicInteger(0);
            int total = (int) totalFiles;
            log.info("Importing SUBJECT [" + subject.getMainName() + "] " + "for USER [" + userId + "]...");

            // BƯỚC 2: Duyệt và Import
            try (Stream<Path> stream = Files.list(folderPath)) {
                stream.filter(Files::isRegularFile).forEach(file -> {
                    String fileName = file.getFileName().toString();

                    // --- REPORT PROGRESS: START ---
                    int current = currentCount.incrementAndGet();
                    reportProgress(userId, subject.getMainName(), fileName, "Importing...", 0, current, total);
                    // -----------------------------

                    try {
                        if (isVideoFile(fileName)) { // Gọi hàm helper cũ
                            String fileRelPath = mapping.getRelativePath() + "\\" + fileName;

                            if (fileNodeRepository.findByVolumeIdAndRelativePath(volume.getId(), fileRelPath)
                                    .isPresent()) {
                                log.info("Duplicate File!");
                                reportProgress(userId, subject.getMainName(), fileName, "Skipped (Exists)", 100,
                                        current, total);
                                return;
                            }

                            String hash = calculateQuickHash(file);

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

                            // --- REPORT PROGRESS: DONE ---
                            reportProgress(userId, subject.getMainName(), fileName, "Saved", 100, current, total);
                            // -----------------------------

                            log.info("File Mapped [" + current + "/" + total + "]: " + fileName);

                            // === 2. GỌI XỬ LÝ MEDIA (METADATA & THUMBNAIL) ===
                            // QUAN TRỌNG: Sử dụng TransactionSynchronizationManager để đợi Commit xong mới
                            // chạy Async
                            // Nếu gọi trực tiếp mediaService.processMedia() ở đây, luồng Async sẽ chạy
                            // trước khi DB có dữ liệu -> Lỗi FK
                            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    try {
                                        // Lúc này FileNode đã nằm an toàn trong DB, ta có thể tạo Metadata
                                        mediaService.processMedia(fileNode, file);
                                    } catch (Exception e) {
                                        log.error("Error processing media background: " + fileName, e);
                                    }
                                }
                            });
                        }
                    } catch (Exception e) {
                        // Log lỗi file lẻ nhưng không ném exception để không rollback cả folder
                        log.error("Skipping file " + file + ": " + e.getMessage());
                        reportProgress(userId, subject.getMainName(), fileName, "Error: " + e.getMessage(), 100,
                                current, total);
                    }

                    log.info("-----");
                });
            } catch (IOException e) {
                log.error("Error reading folder", e);
            }
        } catch (Exception e) {
            log.error("Error count total file", e);
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

            List<String> lowerNames = names.stream().map(String::toLowerCase).toList();
            log.info("   Subject Names parse: {}", names);

            Optional<ContentSubject> subjectOpt = subjectRepository.findFirstByAnyName(lowerNames);

            if (subjectOpt.isEmpty()) {
                // ĐIỂM MÙ SỐ 1: Subject chưa có trong DB (Import mới) -> Bỏ qua check trùng
                log.warn(
                        "   [SKIP] Subject with names {} does not exist in the database -> No duplicates allowed (New Import Logic).",
                        names);
                continue;
            }

            ContentSubject subject = subjectOpt.get();
            String mainName = subject.getMainName();
            log.info("   Subject ID found: {}", subject.getId());

            List<DuplicateFileGroup.DuplicatePair> pairs = new ArrayList<>();

            try (Stream<Path> stream = Files.list(folderPath)) {
                List<Path> importFiles = stream.filter(Files::isRegularFile).filter(p -> isVideoFile(p.toString()))
                        .toList();
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
                        // File này kích thước độc nhất -> Chắc chắn không trùng -> Bỏ qua Hash cho
                        // nhanh
                        // log.debug(" File '{}' ({} bytes) không có candidate cùng size -> OK.",
                        // fileName, size);
                    } else {
                        log.info("      File '{}' ({} bytes) has {} candidate of the same size -> Start Hash...",
                                fileName, size, candidates.size());

                        String importHash = calculateQuickHash(importFile);

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

    public void executeResolvedImport(ImportResolution resolution, String userId) {
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
                            // LOGIC MỚI: TÁI SỬ DỤNG DATABASE CŨ CHO FILE MỚI
                            // 1. Tìm bản ghi cũ trong DB
                            FileNode existingNode = fileNodeRepository.findById(action.getExistingFileId())
                                    .orElse(null);

                            if (existingNode != null) {
                                // 2. Xóa file vật lý CŨ
                                StorageVolume oldVol = storageVolumeService.getVolumeById(existingNode.getVolumeId());
                                if (oldVol != null) {
                                    Path oldPhysicalPath = Paths.get(oldVol.getMountPoint(),
                                            existingNode.getRelativePath());
                                    log.info("KEEP_NEW: Xóa file vật lý cũ tại: " + oldPhysicalPath);
                                    try {
                                        Files.deleteIfExists(oldPhysicalPath);
                                    } catch (IOException ex) {
                                        log.warn("Không thể xóa file cũ (có thể đã mất): " + ex.getMessage());
                                    }
                                }

                                // 3. Cập nhật bản ghi DB trỏ sang vị trí MỚI
                                // Lấy Volume của file mới
                                StorageVolume newVol = storageVolumeService.getVolumeFromPath(action.getNewFilePath());
                                if (newVol != null) {
                                    String root = newVol.getMountPoint();
                                    String fullPathStr = newFile.toString();

                                    // Tính toán relative path mới
                                    // VD: Full = F:\TEST\Video.mp4, Root = F:\ -> Relative = \TEST\Video.mp4
                                    String newRelPath = fullPathStr.substring(root.length());
                                    if (!newRelPath.startsWith(File.separator)) {
                                        newRelPath = File.separator + newRelPath;
                                    }

                                    // Cập nhật thông tin Node
                                    existingNode.setVolumeId(newVol.getId());
                                    existingNode.setRelativePath(newRelPath);
                                    existingNode.setName(newFile.getFileName().toString());
                                    existingNode.setSize(Files.size(newFile));
                                    // existingNode.setFileHash(...); // Hash giống nhau nên không cần update

                                    // Cập nhật Mapping ID mới nếu file mới nằm trong thư mục Subject khác (ít khi
                                    // xảy ra nhưng nên làm)
                                    // (Ở đây ta tạm giữ mapping cũ hoặc cần logic phức tạp hơn để tìm mapping mới,
                                    // nhưng vì thường là cùng Subject nên Mapping giữ nguyên là ổn)
                                    fileNodeRepository.save(existingNode);
                                    log.info(
                                            "KEEP_NEW: Đã cập nhật DB Node {} trỏ sang đường dẫn mới. Giữ nguyên Thumbnail/Metadata.",
                                            existingNode.getId());
                                } else {
                                    log.error("KEEP_NEW Lỗi: Không tìm thấy Volume cho đường dẫn mới: "
                                            + action.getNewFilePath());
                                }
                            }
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

        // 2. Tiếp tục quy trình Import các file còn lại
        // Lưu ý: Với KEEP_NEW, do ta đã update DB trỏ vào file mới,
        // hàm importWithMapping sẽ thấy file này đã tồn tại trong DB (do check
        // RelativePath) -> và sẽ TỰ ĐỘNG BỎ QUA.
        // Điều này rất hoàn hảo!
        importWithMapping(resolution.getSafePaths(), userId);
    }

    /**
     * Tính Hash MD5 với hiệu năng cao (Buffer 64KB) và báo cáo tiến độ qua SSE.
     */
    private String calculateFileHashWithProgress(Path path, String userId, String subjectName, int currentIdx,
            int totalFiles) {
        // 1. Tăng Buffer lên 64KB (Nhanh hơn gấp 64 lần so với 1KB cũ)
        byte[] buffer = new byte[64 * 1024];

        try (FileInputStream fis = new FileInputStream(path.toFile()); // Dùng BufferedInputStream để hệ điều hành tối
                                                                       // ưu việc đọc trước (Read-ahead)
                BufferedInputStream bis = new BufferedInputStream(fis)) {

            MessageDigest digest = MessageDigest.getInstance("MD5");
            long fileSize = Files.size(path);
            long totalRead = 0;
            long lastReportTime = 0; // Để throttle, không gửi SSE quá dày đặc
            int bytesCount;

            while ((bytesCount = bis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesCount);
                totalRead += bytesCount;

                // --- LOGIC BÁO CÁO TIẾN ĐỘ ---
                long now = System.currentTimeMillis();
                // Chỉ gửi báo cáo mỗi 500ms (nửa giây) một lần để tránh spam Client/Server
                if (fileSize > 0 && (now - lastReportTime > 500)) {
                    int percent = (int) ((totalRead * 100) / fileSize);

                    // Gửi SSE
                    reportProgress(userId, subjectName, path.getFileName().toString(),
                            "Hashing (" + percent + "%)", percent, currentIdx, totalFiles);

                    lastReportTime = now;
                }
            }

            // Tính toán chuỗi Hex cuối cùng
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (Exception e) {
            log.error("Hash error for file: " + path, e);
            return "ERROR_HASH_" + System.currentTimeMillis();
        }
    }

    private String calculateQuickHash(Path path) {
        try {
            long fileSize = Files.size(path);
            long chunkSize = 16 * 1024; // 16KB

            // Nếu file nhỏ (< 48KB), Quick Hash = Full Hash
            if (fileSize < chunkSize * 3) {
                // Gọi hàm full hash với userId = null để không gửi SSE (tránh lỗi null
                // pointer/spam)
                return calculateFileHashWithProgress(path, null, null, 0, 0);
            }

            MessageDigest digest = MessageDigest.getInstance("MD5");

            // 1. Hash kích thước file trước (quan trọng nhất)
            digest.update(String.valueOf(fileSize).getBytes());

            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                byte[] buffer = new byte[(int) chunkSize];

                // 2. Hash đầu file
                raf.seek(0);
                raf.read(buffer);
                digest.update(buffer);

                // 3. Hash giữa file
                raf.seek(fileSize / 2);
                raf.read(buffer);
                digest.update(buffer);

                // 4. Hash cuối file
                raf.seek(fileSize - chunkSize);
                raf.read(buffer);
                digest.update(buffer);
            }

            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return "QUICK_" + sb.toString(); // Tiền tố để phân biệt
        } catch (Exception e) {
            return "ERROR";
        }
    }

    private void reportProgress(String userId, String subject, String fileName, String status, int percent, int current,
            int total) {
        try {
            if (userId == null) {
                log.info("Send report error because USERID is null!");
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
        // 1. Kiểm tra: Nếu đã có Hash VÀ Hash đó là chuẩn mới (bắt đầu bằng QUICK_)
        if (fileNode.getFileHash() != null && fileNode.getFileHash().startsWith("QUICK_")) {
            return fileNode.getFileHash();
        }

        // 2. Nếu chưa có Hash HOẶC Hash là chuẩn cũ (không có QUICK_) -> Tính lại
        log.info("Refreshing Hash for file: {} (Old Hash: {})", fileNode.getName(), fileNode.getFileHash());

        StorageVolume vol = storageVolumeService.getVolumeById(fileNode.getVolumeId());
        if (vol != null) {
            Path path = Paths.get(vol.getMountPoint(), fileNode.getRelativePath());
            if (Files.exists(path)) {
                // Tính Hash mới (Rất nhanh)
                String newHash = calculateQuickHash(path);

                // Cập nhật vào DB ngay lập tức
                fileNode.setFileHash(newHash);
                fileNodeRepository.save(fileNode);

                return newHash;
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
