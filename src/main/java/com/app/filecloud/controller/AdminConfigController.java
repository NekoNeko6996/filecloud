package com.app.filecloud.controller;

import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.entity.SysConfig;
import com.app.filecloud.repository.StorageVolumeRepository;
import com.app.filecloud.repository.SysConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/config")
@RequiredArgsConstructor
public class AdminConfigController {

    private final SysConfigRepository sysConfigRepository;
    private final StorageVolumeRepository volumeRepository;

    private static final String KEY_MOVIE_PATH = "MOVIE_STORE_PHYSICAL_MAIN_PATH";

    @GetMapping
    public String configPage(Model model) {
        // 1. Lấy cấu hình hiện tại
        Optional<SysConfig> configOpt = sysConfigRepository.findByKey(KEY_MOVIE_PATH);
        String displayPath = "";
        String status = "NOT_CONFIGURED";

        if (configOpt.isPresent()) {
            // Value format: "VOLUME_UUID::RELATIVE_PATH" (VD: 123-abc::movie_data)
            String rawValue = configOpt.get().getValue();
            if (rawValue != null && rawValue.contains("::")) {
                String[] parts = rawValue.split("::", 2);
                String volUuid = parts[0];
                String relPath = parts[1];

                // Tìm volume theo UUID để lấy mount point hiện tại (VD: E:\ hoặc F:\)
                Optional<StorageVolume> volOpt = volumeRepository.findByUuid(volUuid);
                if (volOpt.isPresent()) {
                    StorageVolume vol = volOpt.get();
                    // Reconstruct full path: E:\ + movie_data
                    Path fullPath = Paths.get(vol.getMountPoint(), relPath);
                    displayPath = fullPath.toString();
                    
                    if (Files.exists(fullPath)) {
                        status = "ACTIVE";
                    } else {
                        status = "PATH_NOT_FOUND"; // Ổ có kết nối nhưng thư mục bị xóa/đổi tên
                    }
                } else {
                    status = "VOLUME_OFFLINE"; // Ổ cứng chứa dữ liệu này đang không kết nối
                    displayPath = "Volume (" + volUuid + ") is offline";
                }
            } else {
                displayPath = rawValue; // Fallback nếu dữ liệu cũ
            }
        }

        model.addAttribute("moviePath", displayPath);
        model.addAttribute("pathStatus", status);
        return "admin/config";
    }

    @PostMapping("/save-movie-path")
    @Transactional
    public String saveMoviePath(@RequestParam("path") String inputPath, RedirectAttributes redirectAttributes) {
        inputPath = inputPath.trim();
        Path pathObj = Paths.get(inputPath);

        // 1. Validate đường dẫn vật lý
        if (!Files.exists(pathObj) || !Files.isDirectory(pathObj)) {
            redirectAttributes.addFlashAttribute("error", "Path does not exist or is not a directory!");
            return "redirect:/admin/config";
        }

        // 2. Tìm StorageVolume tương ứng
        // Logic: Duyệt qua tất cả volume đang mount, xem inputPath có bắt đầu bằng mountPoint nào không
        List<StorageVolume> volumes = volumeRepository.findAll();
        StorageVolume matchedVolume = null;

        // Chuẩn hóa path để so sánh (tránh lỗi dấu / và \)
        String normalizedInput = pathObj.toAbsolutePath().toString();

        for (StorageVolume vol : volumes) {
            String volPath = Paths.get(vol.getMountPoint()).toAbsolutePath().toString();
            // Kiểm tra xem inputPath có nằm trong volPath không
            if (normalizedInput.startsWith(volPath)) {
                matchedVolume = vol;
                break; 
            }
        }

        if (matchedVolume == null) {
            redirectAttributes.addFlashAttribute("error", "The path is not inside any managed Storage Volume! Please add the Drive to Storage Volumes first.");
            return "redirect:/admin/config";
        }

        // 3. Tính toán Relative Path và Lưu
        // VD: Input: E:\data\movies, Vol: E:\ -> Relative: data\movies
        String volRoot = Paths.get(matchedVolume.getMountPoint()).toAbsolutePath().toString();
        String relativePath = normalizedInput.substring(volRoot.length());
        
        // Xóa dấu separator ở đầu nếu có (\data -> data)
        if (relativePath.startsWith(File.separator)) {
            relativePath = relativePath.substring(1);
        }

        // Format lưu trữ: UUID::RelativePath
        // Điều này giúp hệ thống nhận diện đúng dữ liệu kể cả khi ổ E: đổi thành F:
        String configValue = matchedVolume.getUuid() + "::" + relativePath;

        saveOrUpdateConfig(KEY_MOVIE_PATH, configValue, "Root path for Movie Library (Smart mapped via Volume UUID)");

        redirectAttributes.addFlashAttribute("success", "Configuration saved! Mapped to Volume: " + matchedVolume.getLabel() + " (" + matchedVolume.getMountPoint() + ")");
        return "redirect:/admin/config";
    }

    private void saveOrUpdateConfig(String key, String value, String desc) {
        SysConfig config = sysConfigRepository.findByKey(key).orElse(new SysConfig());
        config.setKey(key);
        config.setValue(value);
        config.setDescription(desc);
        config.setDataType(SysConfig.DataType.STRING);
        config.setIsSystem(true);
        sysConfigRepository.save(config);
    }
}