package com.app.filecloud.service;

import com.app.filecloud.entity.FileNode;
import com.app.filecloud.entity.FileThumbnail;
import com.app.filecloud.entity.MediaMetadata;
import com.app.filecloud.repository.FileThumbnailRepository;
import com.app.filecloud.repository.MediaMetadataRepository;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.webp.WebpDirectory;
import static jakarta.persistence.GenerationType.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.util.Date;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final MediaMetadataRepository metadataRepository;
    private final FileThumbnailRepository thumbnailRepository;
    private final FFmpeg ffmpeg;
    private final FFprobe ffprobe;

    @Value("${app.storage.root:uploads}")
    private String rootUploadDir;

    @Async
    @Transactional
    public void processMedia(FileNode fileNode, Path physicalPath) {
        try {
            File file = physicalPath.toFile();

            if (fileNode.isImage()) {
                processImage(file, fileNode);
            } else if (fileNode.isVideo()) {
                processVideo(file, fileNode);
            }
        } catch (Exception e) {
            log.error("Lỗi xử lý media (" + fileNode.getType() + "): " + fileNode.getName(), e);
        }
    }

    // --- XỬ LÝ ẢNH ---
    private void processImage(File file, FileNode fileNode) {
        // 1. Metadata (Sửa tên hàm cho khớp)
        extractMetadata(file, fileNode.getId());
        // 2. Thumbnail (Sửa tên hàm cho khớp)
        generateThumbnail(file, fileNode.getId(), FileThumbnail.ThumbType.SMALL, 200);
        generateThumbnail(file, fileNode.getId(), FileThumbnail.ThumbType.MEDIUM, 800);
    }

    // --- XỬ LÝ VIDEO ---
    private void processVideo(File file, FileNode fileNode) {
        try {
            // 1. Lấy Metadata bằng FFprobe
            FFmpegProbeResult probeResult = ffprobe.probe(file.getAbsolutePath());
            FFmpegStream videoStream = probeResult.getStreams().stream()
                    .filter(s -> s.codec_type == FFmpegStream.CodecType.VIDEO)
                    .findFirst().orElse(null);

            if (videoStream != null) {
                MediaMetadata meta = MediaMetadata.builder()
                        .fileId(fileNode.getId())
                        .width(videoStream.width)
                        .height(videoStream.height)
                        .durationSeconds((int) probeResult.getFormat().duration)
                        .videoCodec(videoStream.codec_name)
                        .frameRate(videoStream.avg_frame_rate.doubleValue())
                        .build();
                metadataRepository.saveAndFlush(meta);
                log.info("Đã lưu metadata Video: " + fileNode.getName());

                // 2. Tạo Thumbnail từ Video
                Path cacheDir = Paths.get(rootUploadDir, ".cache", "temp_frames");
                if (!Files.exists(cacheDir)) {
                    Files.createDirectories(cacheDir);
                }

                String tempFrameName = fileNode.getId() + "_source.jpg";
                Path tempFramePath = cacheDir.resolve(tempFrameName);

                // Dùng FFmpeg chụp ảnh thumbnail
                FFmpegBuilder builder = new FFmpegBuilder()
                        .setInput(file.getAbsolutePath())
                        .addOutput(tempFramePath.toString())
                        .setFrames(1)
                        .setVideoFilter("select='gte(n\\,150)'") // Lấy frame thứ 150
                        .setFormat("image2")
                        .done();

                FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
                executor.createJob(builder).run();

                // Resize ảnh thumbnail vừa chụp (Sửa tên hàm cho khớp)
                File sourceFrame = tempFramePath.toFile();
                if (sourceFrame.exists()) {
                    generateThumbnail(sourceFrame, fileNode.getId(), FileThumbnail.ThumbType.SMALL, 200);
                    generateThumbnail(sourceFrame, fileNode.getId(), FileThumbnail.ThumbType.MEDIUM, 800);

                    // Xóa file tạm (Optional)
                    // Files.deleteIfExists(tempFramePath);
                }
            }
        } catch (Exception e) {
            log.error("Lỗi xử lý Video: " + e.getMessage());
        }
    }

    // --- CÁC HÀM HELPER (Đổi tên về chuẩn: extractMetadata & generateThumbnail) ---
    private void extractMetadata(File file, String fileId) {
        try {
            MediaMetadata mediaMeta = MediaMetadata.builder().fileId(fileId).build();

            // CÁCH 1: Dùng Metadata-Extractor
            try {
                Metadata metadata = ImageMetadataReader.readMetadata(file);
                extractDimensions(metadata, mediaMeta);

                ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                if (exifDir != null) {
                    Date date = exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                    if (date != null) {
                        mediaMeta.setTakenAt(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                    }
                }
            } catch (Exception e) {
                // CÁCH 2: Fallback ImageIO
                try {
                    BufferedImage bimg = ImageIO.read(file);
                    if (bimg != null) {
                        mediaMeta.setWidth(bimg.getWidth());
                        mediaMeta.setHeight(bimg.getHeight());
                    }
                } catch (Exception ex) {
                }
            }

            metadataRepository.save(mediaMeta);
            log.info("Đã lưu metadata cho file: " + fileId);

        } catch (Exception e) {
            log.error("Lỗi trích xuất metadata: " + e.getMessage());
        }
    }

    private void extractDimensions(Metadata metadata, MediaMetadata mediaMeta) {
        // JPEG
        JpegDirectory jpegDir = metadata.getFirstDirectoryOfType(JpegDirectory.class);
        if (jpegDir != null) {
            try {
                mediaMeta.setWidth(jpegDir.getInt(JpegDirectory.TAG_IMAGE_WIDTH));
                mediaMeta.setHeight(jpegDir.getInt(JpegDirectory.TAG_IMAGE_HEIGHT));
                return;
            } catch (Exception e) {
            }
        }
        // PNG
        PngDirectory pngDir = metadata.getFirstDirectoryOfType(PngDirectory.class);
        if (pngDir != null) {
            try {
                mediaMeta.setWidth(pngDir.getInt(PngDirectory.TAG_IMAGE_WIDTH));
                mediaMeta.setHeight(pngDir.getInt(PngDirectory.TAG_IMAGE_HEIGHT));
                return;
            } catch (Exception e) {
            }
        }
        // WebP
        WebpDirectory webpDir = metadata.getFirstDirectoryOfType(WebpDirectory.class);
        if (webpDir != null) {
            try {
                mediaMeta.setWidth(webpDir.getInt(WebpDirectory.TAG_IMAGE_WIDTH));
                mediaMeta.setHeight(webpDir.getInt(WebpDirectory.TAG_IMAGE_HEIGHT));
                return;
            } catch (Exception e) {
            }
        }
    }

    private void generateThumbnail(File originalFile, String fileId, FileThumbnail.ThumbType type, int targetSize) {
        try {
            Path thumbDir = Paths.get(rootUploadDir, ".cache", "thumbnails");
            if (!Files.exists(thumbDir)) {
                Files.createDirectories(thumbDir);
            }

            String thumbName = fileId + "_" + type.name().toLowerCase() + ".jpg";
            Path thumbPath = thumbDir.resolve(thumbName);

            Thumbnails.of(originalFile)
                    .size(targetSize, targetSize)
                    .outputQuality(0.8)
                    .toFile(thumbPath.toFile());

            // Check trùng trước khi lưu
            if (thumbnailRepository.findByFileIdAndType(fileId, type).isEmpty()) {
                FileThumbnail thumb = FileThumbnail.builder()
                        .fileId(fileId)
                        .type(type)
                        .storagePath(".cache/thumbnails/" + thumbName)
                        .width(targetSize)
                        .height(targetSize)
                        .build();
                thumbnailRepository.saveAndFlush(thumb);
            }
        } catch (Exception e) {
            log.error("Lỗi tạo thumbnail " + type + ": " + e.getMessage());
        }
    }
    
    // === THÊM HÀM LƯU AVATAR ===
    public String saveAvatar(MultipartFile file) {
        try {
            // 1. Tạo thư mục nếu chưa có
            Path avatarDir = Paths.get(rootUploadDir, "avatars");
            if (!Files.exists(avatarDir)) {
                Files.createDirectories(avatarDir);
            }

            // 2. Tạo tên file ngẫu nhiên để tránh trùng
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String extension = "";
            if (originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = java.util.UUID.randomUUID().toString() + extension;

            // 3. Lưu file
            Path targetLocation = avatarDir.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // 4. Trả về đường dẫn (Quy ước: /uploads/avatars/...)
            // Lưu ý: Bạn cần cấu hình ResourceHandler để serve đường dẫn này, 
            // hoặc tạo API đọc file. Ở đây mình sẽ trả về đường dẫn tương đối để Controller xử lý.
            return "/avatars/" + fileName;

        } catch (IOException e) {
            throw new RuntimeException("Could not store avatar file. Error: " + e.getMessage());
        }
    }
}
