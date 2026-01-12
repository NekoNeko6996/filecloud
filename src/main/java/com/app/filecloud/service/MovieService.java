package com.app.filecloud.service;

import com.app.filecloud.entity.Movie;
import com.app.filecloud.entity.MovieAlternativeTitle;
import com.app.filecloud.entity.MovieEpisode;
import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.entity.Studio;
import com.app.filecloud.entity.SysConfig;
import com.app.filecloud.entity.Tag;
import com.app.filecloud.repository.MovieAlternativeTitleRepository;
import com.app.filecloud.repository.MovieEpisodeRepository;
import com.app.filecloud.repository.MovieRepository;
import com.app.filecloud.repository.StorageVolumeRepository;
import com.app.filecloud.repository.StudioRepository;
import com.app.filecloud.repository.SysConfigRepository;
import com.app.filecloud.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.app.filecloud.repository.spec.MovieSpecification; // Import class mới tạo
import org.springframework.data.jpa.domain.Specification;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final SysConfigRepository sysConfigRepository;
    private final StorageVolumeRepository volumeRepository;
    private final TagRepository tagRepository;
    private final StudioRepository studioRepository;
    private final MovieAlternativeTitleRepository movieAlternativeTitleRepository;
    private final MovieEpisodeRepository movieEpisodeRepository;

    private final FFmpeg ffmpeg;
    private final FFprobe ffprobe;

    private static final String CONFIG_KEY = "MOVIE_STORE_PHYSICAL_MAIN_PATH";

    // --- 1. HÀM LẤY ĐƯỜNG DẪN GỐC TỪ CONFIG ---
    public Path getMovieLibraryRoot() {
        SysConfig config = sysConfigRepository.findByKey(CONFIG_KEY)
                .orElseThrow(() -> new RuntimeException("Movie Library Path not configured in System Settings!"));

        String rawValue = config.getValue(); // Format: UUID::RelativePath
        if (rawValue == null || !rawValue.contains("::")) {
            throw new RuntimeException("Invalid config format for Movie Path.");
        }

        String[] parts = rawValue.split("::", 2);
        String volUuid = parts[0];
        String relPath = parts[1];

        StorageVolume volume = volumeRepository.findByUuid(volUuid)
                .orElseThrow(() -> new RuntimeException("Storage Volume for Movie Library is missing or offline!"));

        // Ghép: E:\ + data\movies
        return Paths.get(volume.getMountPoint(), relPath);
    }

    // --- 2. TẠO MOVIE MỚI ---
    @Transactional
    public void createMovie(String title, Integer year, String description,
            Double rating, String studioInput, String tagInput, // [NEW] Thêm tham số
            MultipartFile coverFile) throws IOException {
        Path libRoot = getMovieLibraryRoot();

        // Tạo tên folder chuẩn
        String folderName = title.replaceAll("[^a-zA-Z0-9 ._-]", "") + (year != null ? " (" + year + ")" : "");
        Path movieDir = libRoot.resolve(folderName);
        if (!Files.exists(movieDir)) {
            Files.createDirectories(movieDir);
        }

        String coverPathRel = null;
        String thumbPathRel = null;

        // Xử lý ảnh bìa (Giữ nguyên logic cũ)
        if (coverFile != null && !coverFile.isEmpty()) {
            // ... (Logic lưu ảnh bìa và tạo thumbnail giữ nguyên như cũ) ...
            // Copy đoạn logic xử lý file từ code cũ vào đây
            // Hoặc để gọn tôi chỉ ghi chú là giữ nguyên logic xử lý file
            String originalFilename = coverFile.getOriginalFilename();
            String ext = originalFilename != null && originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String coverName = "cover" + ext;
            Path destCover = movieDir.resolve(coverName);
            Files.copy(coverFile.getInputStream(), destCover, StandardCopyOption.REPLACE_EXISTING);
            coverPathRel = folderName + File.separator + coverName;

            // Tạo thumbnail...
            Path thumbDir = movieDir.resolve(".thumbs");
            if (!Files.exists(thumbDir)) {
                Files.createDirectories(thumbDir);
            }
            String thumbName = "thumb_" + coverName;
            Path destThumb = thumbDir.resolve(thumbName);
            try {
                Thumbnails.of(destCover.toFile()).size(300, 450).outputQuality(0.8).toFile(destThumb.toFile());
                thumbPathRel = folderName + File.separator + ".thumbs" + File.separator + thumbName;
            } catch (Exception e) {
                try {
                    Files.copy(destCover, destThumb, StandardCopyOption.REPLACE_EXISTING);
                    thumbPathRel = folderName + File.separator + ".thumbs" + File.separator + thumbName;
                } catch (IOException io) {
                }
            }
        }

        // Lưu DB
        Movie movie = Movie.builder()
                .title(title)
                .releaseYear(year)
                .description(description)
                .rating(rating) // [NEW] Lưu rating
                .coverImageUrl(coverPathRel)
                .thumbnailPath(thumbPathRel)
                .build();

        // Lưu lần 1 để có ID (nếu cần)
        movie = movieRepository.save(movie);

        // [NEW] Xử lý Studio & Tag (Dùng hàm helper bên dưới)
        processStudios(movie, studioInput);
        processTags(movie, tagInput);

        movieRepository.save(movie);
    }

    public Page<Movie> getAllMovies(Pageable pageable) {
        return movieRepository.findAll(pageable);
    }

    public Movie getMovie(String id) {
        return movieRepository.findById(id).orElseThrow(() -> new RuntimeException("Movie not found"));
    }

    @Transactional
    public void deleteMovie(String id) {
        Movie movie = getMovie(id);
        Path libRoot = getMovieLibraryRoot();

        // 1. Xóa thư mục vật lý
        // Logic: Lấy đường dẫn ảnh bìa (VD: "Inception (2010)/cover.jpg") -> Lấy thư mục cha -> Xóa
        String relPath = movie.getCoverImageUrl();
        if (relPath != null) {
            Path relativeFilePath = Paths.get(relPath);
            Path movieFolder = relativeFilePath.getParent(); // Lấy folder "Inception (2010)"

            if (movieFolder != null) {
                Path absoluteFolder = libRoot.resolve(movieFolder);
                deleteFolderRecursively(absoluteFolder); // Gọi hàm xóa đệ quy
            }
        }

        // 2. Xóa trong Database
        movieRepository.delete(movie);
    }

    // Hàm phụ trợ để xóa thư mục chứa nội dung (Java NIO)
    private void deleteFolderRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()) // Xóa file con trước, folder cha sau
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            System.err.println("Could not delete folder: " + path + " - " + e.getMessage());
            // Không throw exception để vẫn tiếp tục xóa DB
        }
    }

    @Transactional
    public void addEpisode(String movieId, String title, Integer episodeNumber, MultipartFile file) throws IOException {
        Movie movie = getMovie(movieId);
        Path libRoot = getMovieLibraryRoot();

        // 1. Xác định thư mục
        String coverRelPath = movie.getCoverImageUrl();
        if (coverRelPath == null) {
            throw new RuntimeException("Movie folder structure invalid");
        }

        Path relativeMovieDir = Paths.get(coverRelPath).getParent();
        Path absoluteMovieDir = libRoot.resolve(relativeMovieDir);

        if (!Files.exists(absoluteMovieDir)) {
            Files.createDirectories(absoluteMovieDir);
        }

        // 2. Lưu file video
        String fileName = file.getOriginalFilename();
        Path destVideo = absoluteMovieDir.resolve(fileName);
        Files.copy(file.getInputStream(), destVideo, StandardCopyOption.REPLACE_EXISTING);

        String videoRelPath = relativeMovieDir.resolve(fileName).toString();
        String thumbRelPath = null;
        int duration = 0;

        // --- 3. [NEW] XỬ LÝ MEDIA (Lấy Duration + Tạo Thumbnail) ---
        try {
            // A. Lấy thông tin video bằng FFprobe
            FFmpegProbeResult probeResult = ffprobe.probe(destVideo.toAbsolutePath().toString());
            duration = (int) probeResult.getFormat().duration; // Lấy thời lượng (giây)

            // B. Tạo Thumbnail
            Path thumbDir = absoluteMovieDir.resolve(".thumbs");
            if (!Files.exists(thumbDir)) {
                Files.createDirectories(thumbDir);
            }

            String thumbName = "ep_" + (episodeNumber != null ? episodeNumber : "x") + "_" + System.currentTimeMillis() + ".jpg";
            Path destThumb = thumbDir.resolve(thumbName);

            // Gọi hàm tạo thumb thông minh (copy từ MediaService)
            generateSmartVideoThumbnail(destVideo.toAbsolutePath().toString(), destThumb.toAbsolutePath().toString(), duration);

            thumbRelPath = relativeMovieDir.resolve(".thumbs").resolve(thumbName).toString();

        } catch (Exception e) {
            System.err.println("Media processing warning: " + e.getMessage());
            // Không throw lỗi để vẫn lưu được file
        }

        // 4. Lưu Entity
        MovieEpisode episode = new MovieEpisode();
        episode.setMovie(movie);
        episode.setTitle(title);
        episode.setEpisodeNumber(episodeNumber != null ? episodeNumber : movie.getEpisodes().size() + 1);
        episode.setFilePath(videoRelPath);
        episode.setThumbnailPath(thumbRelPath);
        episode.setFileSize(file.getSize());
        episode.setMimeType(file.getContentType());
        episode.setDurationSeconds(duration); // [NEW] Lưu thời lượng

        movie.getEpisodes().add(episode);
        movieRepository.save(movie);
    }

    /**
     * [NEW] Hàm tạo thumbnail thông minh (Logic từ MediaService)
     */
    private void generateSmartVideoThumbnail(String inputPath, String outputPath, int durationSeconds) {
        try {
            // 1. Tính toán thời điểm chụp (20% thời lượng để qua intro)
            long targetMillis = calculateSmartTimestamp(durationSeconds);

            // 2. Dùng FFmpegBuilder để chụp và resize
            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(inputPath)
                    .addOutput(outputPath)
                    .setFrames(1)
                    .setStartOffset(targetMillis, TimeUnit.MILLISECONDS) // Seek đến đúng thời điểm
                    .setVideoFilter("scale=640:-1") // Resize về chiều rộng 640px (giữ tỷ lệ)
                    .setFormat("image2")
                    .done();

            FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
            executor.createJob(builder).run();

        } catch (Exception e) {
            throw new RuntimeException("FFmpeg error: " + e.getMessage());
        }
    }

    /**
     * Logic tính thời gian chụp ảnh (Copy từ MediaService)
     */
    private long calculateSmartTimestamp(double durationSeconds) {
        if (durationSeconds <= 0) {
            return 0;
        }

        // Nếu video ngắn dưới 30s: Lấy chính giữa
        if (durationSeconds < 30) {
            return (long) ((durationSeconds / 2) * 1000);
        }

        // Nếu video dài: Lấy ở mốc 20% (thường là qua intro)
        return (long) ((durationSeconds * 0.20) * 1000);
    }

    @Transactional
    public void updateMovie(
            String id,
            String title,
            Integer releaseYear,
            String description,
            MultipartFile coverFile,
            Double rating,
            String studioInput,
            String tagInput
    ) throws IOException {
        Movie movie = getMovie(id);

        // 1. Cập nhật thông tin cơ bản
        movie.setTitle(title);
        movie.setReleaseYear(releaseYear);
        movie.setDescription(description);
        movie.setRating(rating);

        processStudios(movie, studioInput);
        processTags(movie, tagInput);

        // 2. Nếu có upload ảnh bìa mới -> Xử lý thay thế
        if (coverFile != null && !coverFile.isEmpty()) {
            Path libRoot = getMovieLibraryRoot();

            // Lấy folder hiện tại của phim (dựa trên ảnh cũ hoặc tạo mới nếu chưa có)
            Path movieDir;
            if (movie.getCoverImageUrl() != null) {
                movieDir = libRoot.resolve(movie.getCoverImageUrl()).getParent();
            } else {
                // Fallback: Nếu phim chưa có ảnh bìa, tìm theo folder tên phim (logic cũ)
                // Hoặc đơn giản là tạo folder mới nếu cần (ở đây giả định folder đã có)
                String folderName = title.replaceAll("[^a-zA-Z0-9 ._-]", "") + (releaseYear != null ? " (" + releaseYear + ")" : "");
                movieDir = libRoot.resolve(folderName);
                if (!Files.exists(movieDir)) {
                    Files.createDirectories(movieDir);
                }
            }

            // A. Lưu ảnh gốc mới (Ghi đè cover.jpg hoặc cover.png)
            String originalFilename = coverFile.getOriginalFilename();
            String ext = originalFilename != null && originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String coverName = "cover" + ext; // Luôn đặt tên chuẩn

            Path destCover = movieDir.resolve(coverName);
            Files.copy(coverFile.getInputStream(), destCover, StandardCopyOption.REPLACE_EXISTING);

            // Cập nhật path (thực ra path ko đổi nếu tên file ko đổi, nhưng set lại cho chắc)
            String folderName = movieDir.getFileName().toString();
            String coverRelPath = folderName + File.separator + coverName;
            movie.setCoverImageUrl(coverRelPath);

            // B. Tạo lại Thumbnail
            Path thumbDir = movieDir.resolve(".thumbs");
            if (!Files.exists(thumbDir)) {
                Files.createDirectories(thumbDir);
            }

            String thumbName = "thumb_" + coverName;
            Path destThumb = thumbDir.resolve(thumbName);

            try {
                Thumbnails.of(destCover.toFile())
                        .size(300, 450)
                        .outputQuality(0.8)
                        .toFile(destThumb.toFile());

                String thumbRelPath = folderName + File.separator + ".thumbs" + File.separator + thumbName;
                movie.setThumbnailPath(thumbRelPath);
            } catch (Exception e) {
                System.err.println("Thumbnail update failed (fallback to original): " + e.getMessage());
                // Fallback copy
                Files.copy(destCover, destThumb, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        movie.setUpdatedAt(LocalDateTime.now());
        movieRepository.save(movie);
    }

    // --- HELPER METHODS (Tách ra để dùng chung) ---
    private void processStudios(Movie movie, String input) {
        movie.getStudios().clear();
        if (input != null && !input.trim().isEmpty()) {
            String[] names = input.split("[;,]");
            for (String name : names) {
                String cleanName = name.trim();
                if (!cleanName.isEmpty()) {
                    String slug = toSlug(cleanName);
                    Studio studio = studioRepository.findBySlug(slug)
                            .orElseGet(() -> studioRepository.save(
                            Studio.builder().name(cleanName).slug(slug).build()
                    ));
                    movie.getStudios().add(studio);
                }
            }
        }
    }

    private void processTags(Movie movie, String input) {
        movie.getTags().clear();
        if (input != null && !input.trim().isEmpty()) {
            String[] names = input.split("[;,]");
            for (String name : names) {
                String tagName = name.trim();
                if (!tagName.isEmpty()) {
                    String slug = toSlug(tagName);
                    Tag tag = tagRepository.findBySlug(slug)
                            .orElseGet(() -> tagRepository.save(
                            Tag.builder().name(tagName).slug(slug).colorHex("#6366f1").build()
                    ));
                    movie.getTags().add(tag);
                }
            }
        }
    }

    private String toSlug(String input) {
        if (input == null) {
            return "";
        }
        String nowhitespace = input.trim().replaceAll("\\s+", "-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("").toLowerCase();
    }

    @Transactional
    public void addAlternativeTitle(String movieId, String altTitle, String languageCode) {
        Movie movie = getMovie(movieId);

        MovieAlternativeTitle newTitle = new MovieAlternativeTitle();
        newTitle.setMovie(movie);
        newTitle.setAltTitle(altTitle);
        newTitle.setLanguageCode(languageCode);

        movieAlternativeTitleRepository.save(newTitle);
    }

    @Transactional
    public void deleteAlternativeTitle(String titleId) {
        movieAlternativeTitleRepository.deleteById(titleId);
    }

    public Page<Movie> searchMovies(String keyword, Integer year, String studioId, java.util.List<String> tagIds, Pageable pageable) {
        Specification<Movie> spec = com.app.filecloud.repository.spec.MovieSpecification.filterMovies(keyword, year, studioId, tagIds);
        return movieRepository.findAll(spec, pageable);
    }

    @Transactional
    public void updateEpisode(String episodeId, String title, Integer episodeNumber, MultipartFile file) throws IOException {
        MovieEpisode episode = movieEpisodeRepository.findById(episodeId)
                .orElseThrow(() -> new RuntimeException("Episode not found"));

        // 1. Cập nhật thông tin cơ bản
        episode.setTitle(title);
        episode.setEpisodeNumber(episodeNumber);

        // 2. Nếu có upload video mới -> Thay thế file cũ
        if (file != null && !file.isEmpty()) {
            Path libRoot = getMovieLibraryRoot();

            // A. Xóa file cũ (nếu tồn tại)
            if (episode.getFilePath() != null) {
                Path oldFile = libRoot.resolve(episode.getFilePath());
                Files.deleteIfExists(oldFile);
            }
            // Xóa thumbnail cũ luôn cho sạch
            if (episode.getThumbnailPath() != null) {
                Path oldThumb = libRoot.resolve(episode.getThumbnailPath());
                Files.deleteIfExists(oldThumb);
            }

            // B. Lưu file mới (Logic tương tự addEpisode)
            // Lấy folder phim từ Movie cha
            String coverRelPath = episode.getMovie().getCoverImageUrl();
            Path relativeMovieDir = Paths.get(coverRelPath).getParent(); // Folder phim
            Path absoluteMovieDir = libRoot.resolve(relativeMovieDir);

            if (!Files.exists(absoluteMovieDir)) {
                Files.createDirectories(absoluteMovieDir);
            }

            String fileName = file.getOriginalFilename();
            Path destVideo = absoluteMovieDir.resolve(fileName);
            // Copy file mới (REPLACE nếu trùng tên)
            Files.copy(file.getInputStream(), destVideo, StandardCopyOption.REPLACE_EXISTING);

            // C. Xử lý Metadata & Thumbnail mới
            int duration = 0;
            String thumbRelPath = null;
            try {
                FFmpegProbeResult probeResult = ffprobe.probe(destVideo.toAbsolutePath().toString());
                duration = (int) probeResult.getFormat().duration;

                // Tạo thumb mới
                Path thumbDir = absoluteMovieDir.resolve(".thumbs");
                if (!Files.exists(thumbDir)) {
                    Files.createDirectories(thumbDir);
                }

                String thumbName = "ep_" + episodeNumber + "_" + System.currentTimeMillis() + ".jpg";
                Path destThumb = thumbDir.resolve(thumbName);

                generateSmartVideoThumbnail(destVideo.toAbsolutePath().toString(), destThumb.toAbsolutePath().toString(), duration);
                thumbRelPath = relativeMovieDir.resolve(".thumbs").resolve(thumbName).toString();
            } catch (Exception e) {
                System.err.println("Warning processing new video media: " + e.getMessage());
            }

            // D. Cập nhật Entity
            episode.setFilePath(relativeMovieDir.resolve(fileName).toString());
            episode.setFileSize(file.getSize());
            episode.setMimeType(file.getContentType());
            episode.setDurationSeconds(duration);
            episode.setThumbnailPath(thumbRelPath);
        }

        movieEpisodeRepository.save(episode);
    }

    // --- [NEW] XÓA TẬP PHIM ---
    @Transactional
    public void deleteEpisode(String episodeId, boolean deletePhysicalFile) {
        MovieEpisode episode = movieEpisodeRepository.findById(episodeId)
                .orElseThrow(() -> new RuntimeException("Episode not found"));

        if (deletePhysicalFile) {
            Path libRoot = getMovieLibraryRoot();
            try {
                // Xóa Video
                if (episode.getFilePath() != null) {
                    Files.deleteIfExists(libRoot.resolve(episode.getFilePath()));
                }
                // Xóa Thumbnail
                if (episode.getThumbnailPath() != null) {
                    Files.deleteIfExists(libRoot.resolve(episode.getThumbnailPath()));
                }
            } catch (IOException e) {
                System.err.println("Error deleting physical files: " + e.getMessage());
                // Vẫn tiếp tục xóa DB dù lỗi file
            }
        }

        // Xóa khỏi danh sách của Movie cha (để Hibernate sync)
        episode.getMovie().getEpisodes().remove(episode);
        movieEpisodeRepository.delete(episode);
    }

    @Transactional
    public void updateAlternativeTitle(String titleId, String altTitle, String languageCode) {
        // Tìm và cập nhật
        var entity = movieAlternativeTitleRepository.findById(titleId)
                .orElseThrow(() -> new RuntimeException("Alternative Title not found"));

        entity.setAltTitle(altTitle);
        entity.setLanguageCode(languageCode);

        movieAlternativeTitleRepository.save(entity);
    }
}
