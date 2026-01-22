package com.app.filecloud.service;

import com.app.filecloud.entity.*;
import com.app.filecloud.repository.*;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GalleryService {

    private final GalleryAlbumRepository albumRepository;
    private final GalleryPhotoRepository photoRepository;
    private final SysConfigRepository sysConfigRepository;
    private final StorageVolumeRepository volumeRepository;
    private final GalleryPhotoMetadataRepository metadataRepository;

    private static final String KEY_GALLERY_PATH = "GALLERY_STORAGE_PATH";
    private static final String KEY_DEFAULT_ALBUM_NAME = "GALLERY_DEFAULT_ALBUM_NAME";
    private static final String DEFAULT_ALBUM_NAME_VALUE = "Drop Zone";

    // --- 1. LOGIC ALBUM & DASHBOARD ---
    /**
     * Lấy hoặc tạo Album mặc định cho User
     */
    @Transactional
    public GalleryAlbum getOrCreateDefaultAlbum(String userId) {
        String defaultName = getDefaultAlbumNameFromConfig();

        return albumRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(a -> defaultName.equals(a.getTitle()))
                .findFirst()
                .orElseGet(() -> {
                    GalleryAlbum newAlbum = GalleryAlbum.builder()
                            .id(UUID.randomUUID().toString())
                            .userId(userId)
                            .title(defaultName)
                            .description("Default album for quick uploads")
                            .privacyMode(GalleryAlbum.PrivacyMode.PRIVATE)
                            .build();
                    return albumRepository.save(newAlbum);
                });
    }

    private String getDefaultAlbumNameFromConfig() {
        return sysConfigRepository.findByKey(KEY_DEFAULT_ALBUM_NAME)
                .map(SysConfig::getValue)
                .orElseGet(() -> {
                    SysConfig config = new SysConfig();
                    config.setKey(KEY_DEFAULT_ALBUM_NAME);
                    config.setValue(DEFAULT_ALBUM_NAME_VALUE);
                    config.setDescription("Default album name for Quick Uploads");
                    config.setDataType(SysConfig.DataType.STRING);
                    config.setIsSystem(true);
                    sysConfigRepository.save(config);
                    return DEFAULT_ALBUM_NAME_VALUE;
                });
    }

    public List<GalleryAlbum> getUserAlbums(String userId) {
        return albumRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<GalleryPhoto> getAlbumPhotos(String albumId) {
        return photoRepository.findByAlbumIdOrderByCreatedAtDesc(albumId);
    }

    public GalleryAlbum getAlbumById(String albumId) {
        return albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid album Id: " + albumId));
    }

    public void createAlbum(GalleryAlbum album) {
        albumRepository.save(album);
    }

    /**
     * Hàm đọc metadata từ file ảnh bằng thư viện metadata-extractor
     */
    private void extractAndSaveExif(GalleryPhoto photo, Path originalFilePath) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(originalFilePath.toFile());

            GalleryPhotoMetadata exifEntity = GalleryPhotoMetadata.builder()
                    .photo(photo) // Link với Photo
                    .photoId(photo.getId())
                    .build();

            // --- Lấy thông tin từ các Directory ---
            // 1. Exif IFD0 (Make, Model)
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                exifEntity.setCameraMake(ifd0.getString(ExifIFD0Directory.TAG_MAKE));
                exifEntity.setCameraModel(ifd0.getString(ExifIFD0Directory.TAG_MODEL));
            }

            // 2. Exif SubIFD (Date, ISO, Aperture, Shutter, Lens)
            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (subIfd != null) {
                // Ngày chụp
                Date date = subIfd.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (date != null) {
                    exifEntity.setTakenAt(LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
                }

                // Thông số kỹ thuật
                exifEntity.setIso(subIfd.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
                exifEntity.setAperture(subIfd.getDoubleObject(ExifSubIFDDirectory.TAG_FNUMBER));
                exifEntity.setFocalLength(subIfd.getDoubleObject(ExifSubIFDDirectory.TAG_FOCAL_LENGTH));
                exifEntity.setLensModel(subIfd.getString(ExifSubIFDDirectory.TAG_LENS_MODEL));

                // Shutter Speed (Cần xử lý chút vì nó thường trả về số thập phân kiểu 0.01666)
                // Thư viện có hàm lấy Description dạng "1/60 sec" rất tiện
                String shutterDesc = subIfd.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
                if (shutterDesc != null) {
                    exifEntity.setShutterSpeed(shutterDesc.replace(" sec", "")); // Bỏ chữ " sec" cho gọn
                }
            }

            // 3. GPS
            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir != null && gpsDir.getGeoLocation() != null) {
                exifEntity.setGpsLat(gpsDir.getGeoLocation().getLatitude());
                exifEntity.setGpsLng(gpsDir.getGeoLocation().getLongitude());
            }

            // Lưu vào DB
            metadataRepository.save(exifEntity);

        } catch (Exception e) {
            // Không throw exception để tránh làm hỏng luồng upload chính nếu ảnh không có exif
            System.err.println("Could not extract EXIF for " + photo.getOriginalFilename() + ": " + e.getMessage());
        }
    }

    // --- 2. LOGIC UPLOAD (CORE) ---
    @Transactional
    public Map<String, Object> uploadPhotoWithDuplicateCheck(MultipartFile file, String albumId, boolean forceKeep, String currentUserId) throws Exception {
        // A. Xác định Album đích
        if (albumId == null || albumId.isEmpty()) {
            albumId = getOrCreateDefaultAlbum(currentUserId).getId();
        }

        // B. Lấy và Parse Config lưu trữ
        SysConfig pathConfig = sysConfigRepository.findByKey(KEY_GALLERY_PATH)
                .orElseThrow(() -> new RuntimeException("Gallery storage path is not configured!"));
        String rawConfigValue = pathConfig.getValue();
        if (rawConfigValue == null || !rawConfigValue.contains("::")) {
            throw new RuntimeException("Invalid gallery configuration format.");
        }

        String[] parts = rawConfigValue.split("::", 2);
        String volUuid = parts[0];
        String baseRelativePath = parts[1];

        // C. Xác định Volume & Path
        StorageVolume volume = volumeRepository.findByUuid(volUuid)
                .orElseThrow(() -> new RuntimeException("Storage Volume (" + volUuid + ") is OFFLINE!"));

        Path volumeRoot = Paths.get(volume.getMountPoint());
        Path albumDir = volumeRoot.resolve(baseRelativePath).resolve(albumId);

        if (!Files.exists(albumDir)) {
            Files.createDirectories(albumDir);
        }

        // D. Chuẩn bị file
        String originalFilename = file.getOriginalFilename();
        if (forceKeep) {
            originalFilename = "Copy_" + System.currentTimeMillis() + "_" + originalFilename;
        }

        String fileExtension = "";
        int dotIndex = originalFilename != null ? originalFilename.lastIndexOf('.') : -1;
        if (dotIndex > 0) {
            fileExtension = originalFilename.substring(dotIndex);
        }
        String physicalFileName = UUID.randomUUID().toString() + fileExtension;

        Path destinationFile = albumDir.resolve(physicalFileName);

        // E. Lưu file vật lý
        file.transferTo(destinationFile);

        // F. Tính Hash
        String fileHash = calculateFileHash(destinationFile);

        // G. Kiểm tra trùng lặp
        if (!forceKeep) {
            Optional<GalleryPhoto> existingPhotoOpt = photoRepository.findFirstByFileHash(fileHash);
            if (existingPhotoOpt.isPresent()) {
                GalleryPhoto existing = existingPhotoOpt.get();
                Map<String, Object> response = new HashMap<>();
                response.put("status", "DUPLICATE");
                response.put("tempPath", destinationFile.toAbsolutePath().toString());
                response.put("newFileName", originalFilename);

                Map<String, Object> oldInfo = new HashMap<>();
                oldInfo.put("id", existing.getId());
                oldInfo.put("name", existing.getOriginalFilename());
                oldInfo.put("albumName", existing.getAlbum() != null ? existing.getAlbum().getTitle() : "Unknown");
                response.put("existing", oldInfo);
                return response;
            }
        }

        // H. Lưu Entity nếu không trùng
        Path relativePathForDb = Paths.get(baseRelativePath, albumId, physicalFileName);

        GalleryPhoto photo = GalleryPhoto.builder()
                .id(UUID.randomUUID().toString())
                .album(albumRepository.getReferenceById(albumId))
                .volumeId(volume.getId())
                .uploaderId(currentUserId)
                .originalFilename(originalFilename)
                .storagePath(relativePathForDb.toString())
                .size(file.getSize())
                .mimeType(file.getContentType())
                .fileHash(fileHash)
                .isProcessed(false)
                .build();

        photo = photoRepository.save(photo);

        // I. Xử lý hậu kỳ (Thumb/Meta)
        processThumbnailAndMetadata(photo, destinationFile);

        return Map.of("status", "SUCCESS", "id", photo.getId());
    }

    // --- 3. LOGIC XỬ LÝ ẢNH (PROCESSING) ---
    @Transactional
    public void processThumbnailAndMetadata(GalleryPhoto photo, Path originalFilePath) {
        try {
            // 1. Đọc ảnh gốc để lấy kích thước (Width/Height)
            // (Làm trước để nếu file lỗi thì dừng luôn)
            BufferedImage originalImage = ImageIO.read(originalFilePath.toFile());
            if (originalImage != null) {
                photo.setWidth(originalImage.getWidth());
                photo.setHeight(originalImage.getHeight());
            }

            // 2. Tạo Thumbnail (Giữ nguyên)
            Path albumDir = originalFilePath.getParent();
            Path thumbDir = albumDir.resolve(".thumbs");
            if (!Files.exists(thumbDir)) {
                Files.createDirectories(thumbDir);
            }

            Path thumbPath = thumbDir.resolve(originalFilePath.getFileName());

            // Resize thông minh: Nếu ảnh quá lớn thì mới resize, nhỏ thì copy
            if (originalImage != null) {
                Thumbnails.of(originalImage)
                        .size(400, 400)
                        .outputQuality(0.85)
                        .toFile(thumbPath.toFile());
            }

            // 3. [NEW] Trích xuất EXIF Metadata
            extractAndSaveExif(photo, originalFilePath);

            // 4. Cập nhật trạng thái
            photo.setProcessed(true);
            photoRepository.save(photo);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error processing photo: " + photo.getOriginalFilename());
        }
    }

    public String calculateFileHash(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    // --- 4. LOGIC RESOLVE DUPLICATE & VIEW ---
    public boolean deletePhysicalFile(String pathStr) {
        try {
            Path path = Paths.get(pathStr);
            if (Files.exists(path)) {
                Files.delete(path);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public Resource getThumbnailResource(String photoId) {
        try {
            GalleryPhoto photo = photoRepository.findById(photoId)
                    .orElseThrow(() -> new RuntimeException("Photo not found"));

            StorageVolume volume = volumeRepository.findById(photo.getVolumeId())
                    .orElseThrow(() -> new RuntimeException("Volume offline"));

            Path volumeRoot = Paths.get(volume.getMountPoint());
            Path originalPath = volumeRoot.resolve(photo.getStoragePath());
            Path thumbPath = originalPath.getParent().resolve(".thumbs").resolve(originalPath.getFileName());

            if (!Files.exists(thumbPath)) {
                thumbPath = originalPath; // Fallback to original
            }
            return new UrlResource(thumbPath.toUri());
        } catch (Exception e) {
            throw new RuntimeException("Could not load thumbnail", e);
        }
    }

    // --- LOGIC XÓA ẢNH (Batch) ---
    @Transactional
    public void deletePhotos(List<String> photoIds, boolean deletePhysical) {
        List<GalleryPhoto> photos = photoRepository.findAllById(photoIds);

        for (GalleryPhoto photo : photos) {
            // 1. Xóa file vật lý nếu được yêu cầu
            if (deletePhysical) {
                try {
                    // Tái tạo đường dẫn vật lý
                    // Lưu ý: Logic này nên tách ra hàm getPhysicalPath() dùng chung
                    StorageVolume volume = volumeRepository.findById(photo.getVolumeId()).orElse(null);
                    if (volume != null) {
                        Path volumeRoot = Paths.get(volume.getMountPoint());
                        Path filePath = volumeRoot.resolve(photo.getStoragePath());
                        Files.deleteIfExists(filePath);

                        // Xóa luôn thumbnail
                        Path thumbPath = filePath.getParent().resolve(".thumbs").resolve(filePath.getFileName());
                        Files.deleteIfExists(thumbPath);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to delete physical file for: " + photo.getId() + " - " + e.getMessage());
                    // Vẫn tiếp tục xóa DB dù lỗi file (để tránh rác DB)
                }
            }

            // 2. Xóa DB (Cascade sẽ tự xóa Metadata và DeepZoom)
            photoRepository.delete(photo);
        }
    }

    // --- LOGIC ĐỔI TÊN ---
    @Transactional
    public String renamePhoto(String photoId, String newName, boolean renamePhysical) throws Exception {
        GalleryPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found"));

        String oldName = photo.getOriginalFilename();
        String extension = "";
        int dotIndex = oldName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = oldName.substring(dotIndex);
        }

        // Đảm bảo tên mới có đuôi file cũ
        if (!newName.toLowerCase().endsWith(extension.toLowerCase())) {
            newName += extension;
        }

        // Cập nhật DB
        photo.setOriginalFilename(newName);

        // Cập nhật vật lý (Nếu cần) - Lưu ý: Logic này phức tạp nếu file đang bị lock
        if (renamePhysical) {
            StorageVolume volume = volumeRepository.findById(photo.getVolumeId()).orElseThrow();
            Path volumeRoot = Paths.get(volume.getMountPoint());
            Path oldPath = volumeRoot.resolve(photo.getStoragePath());
            Path newPath = oldPath.resolveSibling(newName); // Cùng thư mục, tên mới

            // Rename file thật (chỉ đổi tên file vật lý, không đổi UUID trong storagePath của DB để giữ link ổn định
            // HOẶC: Nếu storagePath lưu tên file thật thì phải update cả storagePath.
            // Trong thiết kế trước: storagePath = ".../UUID.jpg" -> Tên file vật lý là UUID.
            // => originalFilename chỉ là tên hiển thị -> renamePhysical KHÔNG CẦN THIẾT nếu ta quản lý bằng UUID.
            // TUY NHIÊN: Nếu bạn muốn file vật lý đổi tên theo:
            // Files.move(oldPath, newPath);
            // photo.setStoragePath(...) 
        }

        photoRepository.save(photo);
        return newName;
    }

    // --- LOGIC LẤY CHI TIẾT (DTO) ---
    public Map<String, Object> getPhotoDetails(String photoId) {
        GalleryPhoto photo = photoRepository.findById(photoId).orElseThrow();
        Map<String, Object> details = new HashMap<>();

        details.put("id", photo.getId());
        details.put("name", photo.getOriginalFilename());
        details.put("size", photo.getReadableSize());
        details.put("mime", photo.getMimeType());
        details.put("uploaded", photo.getCreatedAt());
        details.put("width", photo.getWidth());
        details.put("height", photo.getHeight());

        if (photo.getMetadata() != null) {
            GalleryPhotoMetadata meta = photo.getMetadata();
            details.put("camera", (meta.getCameraMake() != null ? meta.getCameraMake() : "") + " " + (meta.getCameraModel() != null ? meta.getCameraModel() : ""));
            details.put("iso", meta.getIso());
            details.put("aperture", meta.getAperture());
            details.put("shutter", meta.getShutterSpeed());
            details.put("takenAt", meta.getTakenAt());
            details.put("lens", meta.getLensModel());
        }

        return details;
    }
    
    @Transactional
    public void deleteAlbum(String albumId) {
        // 1. Lấy danh sách ảnh trong album để xóa file vật lý trước
        List<GalleryPhoto> photos = photoRepository.findByAlbumIdOrderByCreatedAtDesc(albumId);
        
        // 2. Xóa vật lý từng ảnh (Tái sử dụng logic deletePhotos)
        // Lưu ý: Cần convert List<GalleryPhoto> sang List<String> IDs nếu hàm deletePhotos nhận ID
        List<String> photoIds = photos.stream().map(GalleryPhoto::getId).toList();
        deletePhotos(photoIds, true); // true = xóa file vật lý

        // 3. Xóa Album khỏi DB
        albumRepository.deleteById(albumId);
    }

    @Transactional
    public void renameAlbum(String albumId, String newTitle) {
        GalleryAlbum album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("Album not found"));
        album.setTitle(newTitle);
        albumRepository.save(album);
    }
}
