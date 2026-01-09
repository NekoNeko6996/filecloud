package com.app.filecloud.controller;

import com.app.filecloud.entity.*;
import com.app.filecloud.repository.*;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/manga")
@RequiredArgsConstructor
public class MangaController {

    private final MangaSeriesRepository mangaRepository;
    private final MangaChapterRepository chapterRepository;
    private final MangaPageRepository pageRepository;
    private final MangaAuthorRepository authorRepository;

    @Value("${app.storage.root:uploads}")
    private String rootUploadDir;

    @GetMapping
    public String mangaList(Model model) {
        List<MangaSeries> mangas = mangaRepository.findAll();
        mangas.sort((a, b) -> {
            if (b.getCreatedAt() == null || a.getCreatedAt() == null) {
                return 0;
            }
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        model.addAttribute("mangas", mangas);
        return "manga/list";
    }

    @GetMapping("/create")
    public String createMangaPage(Model model) {
        return "manga/create";
    }

    // --- LOGIC UPLOAD MỚI (KHÔNG CẦN VOLUME) ---
    @PostMapping("/upload")
    @ResponseBody
    @Transactional
    public String uploadManga(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("title") String title,
            @RequestParam("author") String authorName,
            @RequestParam("description") String description,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {

        try {
            // 1. Xử lý Author
            MangaAuthor author = authorRepository.findByName(authorName)
                    .orElseGet(() -> authorRepository.save(MangaAuthor.builder().name(authorName).build()));

            // 2. Xử lý Manga Series
            MangaSeries manga = new MangaSeries();
            manga.setTitle(title);
            manga.setDescription(description);
            manga.setStatus(MangaSeries.Status.ONGOING);
            manga.setAuthors(Set.of(author));
            manga.setReleaseYear(String.valueOf(java.time.Year.now().getValue()));

            // Lưu Cover (Dùng đường dẫn tương đối /manga/covers/...)
            if (cover != null && !cover.isEmpty()) {
                String coverName = System.currentTimeMillis() + "_" + cover.getOriginalFilename();
                // Path vật lý: {root}/manga/covers/
                Path coverDir = Paths.get(rootUploadDir, "manga", "covers");
                if (!Files.exists(coverDir)) {
                    Files.createDirectories(coverDir);
                }

                cover.transferTo(coverDir.resolve(coverName));
                manga.setCoverPath("/manga/covers/" + coverName);
            }
            manga = mangaRepository.save(manga);

            // 3. Xử lý File (ZIP hoặc Folder)
            if (files.length == 1 && files[0].getOriginalFilename() != null && files[0].getOriginalFilename().toLowerCase().endsWith(".zip")) {
                processZipFile(files[0], manga);
                return "Upload ZIP success!";
            }

            processFolderUpload(files, manga);
            return "Upload Folder success!";

        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    // Hàm xử lý chung cho việc lưu file ảnh
    private void saveImage(InputStream inputStream, MangaSeries manga, String chapterName, String fileName, long inputSize, Map<String, MangaChapter> chapterCache) throws Exception {
        MangaChapter chapter = chapterCache.get(chapterName);

        if (chapter == null) {
            chapter = chapterRepository.findByMangaIdAndChapterName(manga.getId(), chapterName);

            if (chapter == null) {
                // Bước 1: Lưu tạm để lấy ID
                chapter = MangaChapter.builder()
                        .manga(manga)
                        .chapterName(chapterName)
                        .folderPath("")
                        .build();
                chapter = chapterRepository.save(chapter);

                // Bước 2: Tạo đường dẫn tương đối
                String relativePath = Paths.get("manga", "content", manga.getId(), chapter.getId()).toString();
                Path absolutePath = Paths.get(rootUploadDir, relativePath);
                if (!Files.exists(absolutePath)) {
                    Files.createDirectories(absolutePath);
                }

                // Bước 3: Cập nhật đường dẫn
                chapter.setFolderPath(relativePath);
                chapter = chapterRepository.save(chapter);
            }
            chapterCache.put(chapterName, chapter);
        }

        // Lưu file vào ổ cứng
        Path destFile = Paths.get(rootUploadDir, chapter.getFolderPath(), fileName);

        // --- SỬA ĐỔI QUAN TRỌNG TẠI ĐÂY ---
        // Files.copy trả về số byte thực tế đã ghi. Ta dùng giá trị này làm size chuẩn.
        long actualSize = Files.copy(inputStream, destFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        MangaPage page = MangaPage.builder()
                .chapter(chapter)
                .fileName(fileName)
                .size(actualSize) // <--- Dùng size thực tế vừa ghi, không dùng tham số inputSize nữa
                .pageOrder(extractPageNumber(fileName))
                .build();
        pageRepository.save(page);
    }

    // --- API ĐỌC ẢNH (Quan trọng: Ghép đường dẫn tương đối với Root) ---
    @GetMapping("/image/{pageId}")
    @ResponseBody
    public ResponseEntity<Resource> getMangaImage(@PathVariable String pageId) {
        try {
            MangaPage page = pageRepository.findById(pageId).orElseThrow();

            // LOGIC MỚI: Ghép root + folderPath tương đối + tên file
            Path imagePath = Paths.get(rootUploadDir, page.getChapter().getFolderPath(), page.getFileName());

            Resource resource = new UrlResource(imagePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // --- HÀM XỬ LÝ ZIP MỚI ---
    private void processZipFile(MultipartFile zipFile, MangaSeries manga) throws Exception {
        Map<String, MangaChapter> chapterCache = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName(); // VD: "Naruto/Chap 1/01.jpg"
                String[] parts = entryName.split("/");

                // Cần ít nhất: FolderChap/Image
                if (parts.length < 2) {
                    continue;
                }

                String fileName = parts[parts.length - 1];
                String chapterName = parts[parts.length - 2];

                if (isImageFile(fileName)) {
                    saveImage(zis, manga, chapterName, fileName, entry.getSize(), chapterCache);
                }
            }
        }
    }

    // --- HÀM XỬ LÝ FOLDER CŨ (Tách ra từ hàm upload cũ) ---
    private void processFolderUpload(MultipartFile[] files, MangaSeries manga) throws Exception {
        Map<String, MangaChapter> chapterCache = new HashMap<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String originalPath = file.getOriginalFilename();
            if (originalPath == null) {
                continue;
            }

            String[] parts = originalPath.split("/");
            if (parts.length < 2) {
                continue;
            }

            String fileName = parts[parts.length - 1];
            String chapterName = parts[parts.length - 2];

            if (isImageFile(fileName)) {
                // Với MultipartFile, ta dùng file.getInputStream() và file.getSize()
                saveImage(file.getInputStream(), manga, chapterName, fileName, file.getSize(), chapterCache);
            }
        }
    }

    private boolean isImageFile(String fileName) {
        if (fileName.startsWith(".") || fileName.equalsIgnoreCase("thumbs.db")) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private int extractPageNumber(String fileName) {
        try {
            // Loại bỏ phần đuôi file (.jpg, .png...) trước
            int dotIndex = fileName.lastIndexOf('.');
            String nameWithoutExt = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);

            // Regex tìm tất cả các cụm số
            Pattern p = Pattern.compile("(\\d+)");
            Matcher m = p.matcher(nameWithoutExt);

            String lastNumber = "0";
            // Duyệt để lấy cụm số cuối cùng tìm thấy
            while (m.find()) {
                lastNumber = m.group();
            }

            // VD: "12345_p14" -> Tìm thấy "12345" rồi "14" -> Lấy "14"
            return Integer.parseInt(lastNumber);
        } catch (Exception e) {
            return 999; // Fallback nếu không tìm thấy số nào
        }
    }

    @GetMapping("/{id}")
    public String mangaProfile(@PathVariable String id, Model model) {
        MangaSeries manga = mangaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Manga not found"));

        // Lấy list chapter (Sắp xếp theo tên hoặc ngày tạo)
        List<MangaChapter> chapters = chapterRepository.findByMangaIdOrderByChapterNameAsc(id);

        // Sắp xếp lại chapter theo logic số học (Chap 1, Chap 2, Chap 10...) thay vì String (1, 10, 2)
        // Đây là xử lý java đơn giản, sau này có thể tối ưu DB
        chapters.sort((c1, c2) -> {
            return extractPageNumber(c1.getChapterName()) - extractPageNumber(c2.getChapterName());
        });

        model.addAttribute("manga", manga);
        model.addAttribute("chapters", chapters);
        return "manga/detail";
    }

    // 5. Màn hình Đọc (Xem ảnh trong chapter)
    @GetMapping("/read/{chapterId}")
    public String readChapter(@PathVariable String chapterId, Model model) {
        MangaChapter currentChapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found"));

        // 1. Lấy danh sách chapter của truyện này và sort đúng thứ tự
        List<MangaChapter> allChapters = chapterRepository.findByMangaIdOrderByChapterNameAsc(currentChapter.getManga().getId());
        allChapters.sort((c1, c2) -> extractPageNumber(c1.getChapterName()) - extractPageNumber(c2.getChapterName()));

        // 2. Tìm vị trí chapter hiện tại
        int currentIndex = -1;
        for (int i = 0; i < allChapters.size(); i++) {
            if (allChapters.get(i).getId().equals(currentChapter.getId())) {
                currentIndex = i;
                break;
            }
        }

        // 3. Xác định Next và Prev
        // Giả sử list sort tăng dần: Chap 1 (idx 0), Chap 2 (idx 1)...
        // Prev là index - 1, Next là index + 1
        MangaChapter prevChapter = (currentIndex > 0) ? allChapters.get(currentIndex - 1) : null;
        MangaChapter nextChapter = (currentIndex < allChapters.size() - 1) ? allChapters.get(currentIndex + 1) : null;

        // 4. Lấy pages
        List<MangaPage> pages = pageRepository.findByChapterIdOrderByPageOrderAsc(chapterId);

        model.addAttribute("chapter", currentChapter);
        model.addAttribute("pages", pages);
        model.addAttribute("prevChapter", prevChapter);
        model.addAttribute("nextChapter", nextChapter);
        model.addAttribute("allChapters", allChapters); // Để dùng cho Dropdown chọn nhanh

        return "manga/read";
    }

    @PostMapping("/delete")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> deleteManga(@RequestParam("id") String id,
            @RequestParam(defaultValue = "false") boolean deletePhysical) {
        try {
            MangaSeries manga = mangaRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Manga not found"));

            // 1. Xóa file vật lý nếu user yêu cầu
            if (deletePhysical) {
                // A. Xóa Folder nội dung: uploads/manga/content/{MangaID}
                Path contentDir = Paths.get(rootUploadDir, "manga", "content", manga.getId());
                deleteFolderRecursively(contentDir);

                // B. Xóa Ảnh bìa (nếu có)
                if (manga.getCoverPath() != null) {
                    // coverPath dạng "/manga/covers/abc.jpg" -> cần bỏ dấu "/" đầu để ghép với root
                    String relativeCover = manga.getCoverPath().startsWith("/")
                            ? manga.getCoverPath().substring(1)
                            : manga.getCoverPath();

                    Path coverFile = Paths.get(rootUploadDir, relativeCover);
                    Files.deleteIfExists(coverFile);
                }
            }

            // 2. Xóa trong Database (Cascade sẽ tự xóa Chapters và Pages liên quan)
            mangaRepository.delete(manga);

            return ResponseEntity.ok("Deleted successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // Helper: Xóa thư mục và toàn bộ file con
    private void deleteFolderRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted(Comparator.reverseOrder()) // Xóa file con trước, folder sau
                .map(Path::toFile)
                .forEach(java.io.File::delete);
    }
}
