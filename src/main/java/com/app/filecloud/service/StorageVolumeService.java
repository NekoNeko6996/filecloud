package com.app.filecloud.service;

import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.repository.StorageVolumeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageVolumeService {

    private final StorageVolumeRepository volumeRepository;

    @PostConstruct
    public void syncVolumes() {
        log.info("--- START SCANNING THE PHYSICAL HARD DRIVE ---");
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            try {
                FileStore store = Files.getFileStore(root);
                String mountPoint = root.toString(); // VD: "F:\"
                String driveLetter = mountPoint.substring(0, 2); // "F:"

                // 1. Lấy thông tin chi tiết từ lệnh CMD (Label & Serial)
                VolumeInfo info = getVolumeInfo(driveLetter);

                // Ưu tiên lấy Label từ lệnh Vol, nếu không có thì lấy từ Java NIO
                String label = (info.label != null && !info.label.isEmpty()) ? info.label : store.name();
                // Nếu không lấy được Serial, dùng hashCode của mountPoint làm tạm (để không null)
                String uuid = (info.serial != null && !info.serial.isEmpty()) ? info.serial : "UNKNOWN-" + Math.abs(mountPoint.hashCode());

                log.info("Drive scanned: Label='{}', Serial='{}'", mountPoint, label, uuid);

                updateVolumeInDb(label, mountPoint, uuid, store.getTotalSpace(), store.getUsableSpace());

            } catch (Exception e) {
                log.warn("Error reading drive {}: {}", root, e.getMessage());
            }
        }
        log.info("--- SCAN END ---");
    }

    private void updateVolumeInDb(String label, String mountPoint, String uuid, long total, long available) {
        Optional<StorageVolume> volOpt = volumeRepository.findByUuid(uuid);
        StorageVolume vol;

        if (volOpt.isPresent()) {
            vol = volOpt.get();
            // Nếu ổ đổi ký tự (VD: F: -> G:), cập nhật lại
            if (!vol.getMountPoint().equals(mountPoint)) {
                log.info("Update MountPoint: {} -> {}", vol.getMountPoint(), mountPoint);
                vol.setMountPoint(mountPoint);
            }
        } else {
            // Thử tìm theo MountPoint (để tránh tạo trùng nếu UUID bị lỗi đọc)
            vol = volumeRepository.findByMountPoint(mountPoint).orElse(new StorageVolume());
            vol.setUuid(uuid);
            vol.setStatus(StorageVolume.Status.ONLINE);
        }
        
        vol.setMountPoint(mountPoint);

        vol.setLabel((label == null || label.isBlank()) ? "Local Disk (" + mountPoint + ")" : label);
        vol.setTotalCapacity(total);
        vol.setAvailableCapacity(available);
        vol.setLastScannedAt(LocalDateTime.now());

        volumeRepository.save(vol);
    }

    // DTO nội bộ
    private static class VolumeInfo {

        String label = "";
        String serial = "";
    }

    private VolumeInfo getVolumeInfo(String driveLetter) {
        VolumeInfo info = new VolumeInfo();
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", "vol " + driveLetter);
            builder.redirectErrorStream(true);
            Process p = builder.start();

            // Đọc output với encoding mặc định của hệ thống (thường xử lý được tiếng Việt trên Windows hiện đại)
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    // Case 1: Volume Serial Number is 6042-1683 (Tiếng Anh)
                    // Case 2: Số sê-ri của ổ đĩa là 6042-1683 (Tiếng Việt)
                    String lower = line.toLowerCase();

                    if (lower.contains("serial number") || lower.contains("sê-ri")) {
                        // Lấy từ cuối chuỗi lên, cắt bỏ khoảng trắng
                        // VD: "... is 1234-5678" -> lấy "1234-5678"
                        String[] parts = line.split("\\s+"); // Tách theo khoảng trắng
                        if (parts.length > 0) {
                            info.serial = parts[parts.length - 1];
                        }
                    } // Lấy Label (thường là dòng đầu tiên có chữ Volume/Ổ đĩa)
                    else if ((lower.contains("volume") || lower.contains("ổ đĩa")) && lower.contains(driveLetter.toLowerCase())) {
                        // Logic: Tìm chữ " is " hoặc " là "
                        int idx = lower.indexOf(" is ");
                        if (idx == -1) {
                            idx = lower.indexOf(" là ");
                        }

                        if (idx != -1) {
                            info.label = line.substring(idx + 4).trim();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("The vol {} command could not be executed.", driveLetter);
        }
        return info;
    }

    public StorageVolume getVolumeFromPath(String pathStr) {
        Path path = Paths.get(pathStr);
        Path root = path.getRoot();
        if (root == null) {
            return null;
        }
        String mountPoint = root.toString();

        return volumeRepository.findByMountPoint(mountPoint)
                .orElseGet(() -> {
                    // Force sync nếu chưa tìm thấy
                    syncVolumes();
                    return volumeRepository.findByMountPoint(mountPoint).orElse(null);
                });
    }
    
    public StorageVolume getVolumeById(Integer id) {
        if (id == null) return null;
        return volumeRepository.findById(id).orElse(null);
    }
}
