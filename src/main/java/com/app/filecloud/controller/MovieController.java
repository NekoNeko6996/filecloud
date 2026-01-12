package com.app.filecloud.controller;

import com.app.filecloud.entity.Movie;
import com.app.filecloud.entity.MovieEpisode;
import com.app.filecloud.repository.MovieEpisodeRepository;
import com.app.filecloud.repository.MovieRepository;
import com.app.filecloud.repository.StudioRepository;
import com.app.filecloud.repository.TagRepository;
import com.app.filecloud.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

@Controller
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;
    private final MovieEpisodeRepository movieEpisodeRepository;
    private final StudioRepository studioRepository;
    private final TagRepository tagRepository;
    private final MovieRepository movieRepository;

    // --- TRANG LIST ---
    @GetMapping
    public String listPage(Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String studio,
            @RequestParam(required = false) java.util.List<String> tags, // [MOD] Nhận List
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        // 1. Sort
        String[] sortParams = sort.split(",");
        Sort sortObj = Sort.by(Sort.Direction.fromString(sortParams[1]), sortParams[0]);

        // 2. Search (truyền list tags)
        Page<Movie> moviePage = movieService.searchMovies(keyword, year, studio, tags, PageRequest.of(page, 24, sortObj));

        // 3. Model Attributes
        model.addAttribute("movies", moviePage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", moviePage.getTotalPages());

        // Filter params
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedStudio", studio);
        model.addAttribute("selectedTags", tags); // List các ID đã chọn
        model.addAttribute("selectedSort", sort);

        // Dropdown Data
        model.addAttribute("allStudios", studioRepository.findAll(Sort.by("name")));
        model.addAttribute("allTags", tagRepository.findAll(Sort.by("name")));

        // [MOD] Dùng danh sách năm thực tế từ DB
        model.addAttribute("allYears", movieRepository.findDistinctReleaseYears());

        return "movie/list";
    }

    // --- TRANG ADD ---
    @GetMapping("/add")
    public String addPage() {
        return "movie/add";
    }

    @PostMapping("/save")
    public String saveMovie(@RequestParam("title") String title,
            @RequestParam(value = "releaseYear", required = false) Integer year,
            @RequestParam("description") String description,
            @RequestParam(value = "rating", required = false) Double rating, // [NEW]
            @RequestParam(value = "studios", required = false) String studios, // [NEW]
            @RequestParam(value = "tags", required = false) String tags, // [NEW]
            @RequestParam("coverFile") MultipartFile coverFile) {
        try {
            // Gọi hàm createMovie với đầy đủ tham số
            movieService.createMovie(title, year, description, rating, studios, tags, coverFile);
            return "redirect:/movies";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/movies/add?error=" + e.getMessage();
        }
    }

    // --- TRANG DETAIL ---
    @GetMapping("/{id}")
    public String detailPage(@PathVariable String id, Model model) {
        Movie movie = movieService.getMovie(id);
        model.addAttribute("movie", movie);
        return "movie/detail";
    }

    // --- API HIỂN THỊ ẢNH (Cover/Thumbnail) ---
    @GetMapping("/image/{id}")
    @ResponseBody
    public ResponseEntity<Resource> getImage(@PathVariable String id, @RequestParam(defaultValue = "false") boolean thumb) {
        try {
            Movie movie = movieService.getMovie(id);
            Path libRoot = movieService.getMovieLibraryRoot();

            String relPath = thumb && movie.getThumbnailPath() != null ? movie.getThumbnailPath() : movie.getCoverImageUrl();

            if (relPath == null) {
                return ResponseEntity.notFound().build();
            }

            Path imagePath = libRoot.resolve(relPath);
            if (!Files.exists(imagePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(imagePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG) // Hoặc logic detect mime type
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/delete/{id}")
    @ResponseBody
    public ResponseEntity<String> deleteMovie(@PathVariable String id) {
        try {
            movieService.deleteMovie(id); // Gọi service để xóa file và DB
            return ResponseEntity.ok("Deleted successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/episodes/add")
    public String addEpisode(@PathVariable String id,
            @RequestParam("title") String title,
            @RequestParam("episodeNumber") Integer episodeNumber,
            @RequestParam("file") MultipartFile file) {
        try {
            movieService.addEpisode(id, title, episodeNumber, file);
            return "redirect:/movies/" + id;
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/movies/" + id + "?error=" + e.getMessage();
        }
    }

    // API hiển thị ảnh thumbnail của Episode
    @GetMapping("/episodes/image/{episodeId}")
    @ResponseBody
    public ResponseEntity<Resource> getEpisodeImage(@PathVariable String episodeId) {
        try {
            // Tìm Episode trong DB (Bạn có thể dùng MovieEpisodeRepository hoặc qua Service)
            // Giả sử bạn đã inject repository: private final MovieEpisodeRepository episodeRepository;
            MovieEpisode ep = movieEpisodeRepository.findById(episodeId)
                    .orElseThrow(() -> new RuntimeException("Episode not found"));

            if (ep.getThumbnailPath() == null) {
                return ResponseEntity.notFound().build();
            }

            // Đường dẫn gốc lấy từ Service hoặc Config
            Path libRoot = movieService.getMovieLibraryRoot();
            Path imagePath = libRoot.resolve(ep.getThumbnailPath());

            if (!Files.exists(imagePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(imagePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/update")
    public String updateMovie(@PathVariable String id,
            @RequestParam("title") String title,
            @RequestParam(value = "releaseYear", required = false) Integer releaseYear,
            @RequestParam("description") String description,
            @RequestParam(value = "studios", required = false) String studios,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "rating", required = false) Double rating,
            @RequestParam(value = "coverFile", required = false) MultipartFile coverFile) {
        try {
            // [FIX] Sửa thứ tự tham số khớp với Service: coverFile, rating, studios, tags
            movieService.updateMovie(id, title, releaseYear, description, coverFile, rating, studios, tags);
            return "redirect:/movies/" + id;
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/movies/" + id + "?error=" + e.getMessage();
        }
    }

    @GetMapping("/api/suggestions/studios")
    @ResponseBody
    public java.util.List<String> getAllStudioNames() {
        return studioRepository.findAll().stream()
                .map(com.app.filecloud.entity.Studio::getName)
                .collect(java.util.stream.Collectors.toList());
    }

    @GetMapping("/api/suggestions/tags")
    @ResponseBody
    public java.util.List<String> getAllTagNames() {
        return tagRepository.findAll().stream()
                .map(com.app.filecloud.entity.Tag::getName)
                .collect(java.util.stream.Collectors.toList());
    }

    @PostMapping("/{id}/titles/add")
    public String addAltTitle(@PathVariable String id,
            @RequestParam("altTitle") String altTitle,
            @RequestParam(value = "languageCode", required = false) String languageCode) {
        try {
            movieService.addAlternativeTitle(id, altTitle, languageCode);
            return "redirect:/movies/" + id;
        } catch (Exception e) {
            return "redirect:/movies/" + id + "?error=" + e.getMessage();
        }
    }

    @PostMapping("/{movieId}/titles/delete/{titleId}")
    public String deleteAltTitle(@PathVariable String movieId, @PathVariable String titleId) {
        try {
            movieService.deleteAlternativeTitle(titleId);
            return "redirect:/movies/" + movieId;
        } catch (Exception e) {
            return "redirect:/movies/" + movieId + "?error=" + e.getMessage();
        }
    }

    // --- CẬP NHẬT TẬP PHIM ---
    @PostMapping("/episodes/{id}/update")
    public String updateEpisode(@PathVariable String id,
            @RequestParam("title") String title,
            @RequestParam("episodeNumber") Integer episodeNumber,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("movieId") String movieId) { // Cần movieId để redirect về đúng trang
        try {
            movieService.updateEpisode(id, title, episodeNumber, file);
            return "redirect:/movies/" + movieId;
        } catch (Exception e) {
            return "redirect:/movies/" + movieId + "?error=" + e.getMessage();
        }
    }

    // --- XÓA TẬP PHIM ---
    @PostMapping("/episodes/{id}/delete")
    public String deleteEpisode(@PathVariable String id,
            @RequestParam("movieId") String movieId,
            @RequestParam(value = "deleteFile", defaultValue = "false") boolean deleteFile) {
        try {
            movieService.deleteEpisode(id, deleteFile);
            return "redirect:/movies/" + movieId;
        } catch (Exception e) {
            return "redirect:/movies/" + movieId + "?error=" + e.getMessage();
        }
    }
    
    @PostMapping("/{movieId}/titles/update/{titleId}")
    public String updateAltTitle(@PathVariable String movieId,
                                 @PathVariable String titleId,
                                 @RequestParam("altTitle") String altTitle,
                                 @RequestParam(value = "languageCode", required = false) String languageCode) {
        try {
            movieService.updateAlternativeTitle(titleId, altTitle, languageCode);
            return "redirect:/movies/" + movieId;
        } catch (Exception e) {
            return "redirect:/movies/" + movieId + "?error=" + e.getMessage();
        }
    }
}
