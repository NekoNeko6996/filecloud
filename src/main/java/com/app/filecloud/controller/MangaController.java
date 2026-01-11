package com.app.filecloud.controller;

import com.app.filecloud.dto.MangaUpdateDTO;
import com.app.filecloud.entity.*;
import com.app.filecloud.repository.*;
import java.io.File;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.coobird.thumbnailator.Thumbnails;

@Controller
@RequestMapping("/manga")
@RequiredArgsConstructor
public class MangaController {

    private final MangaSeriesRepository mangaRepository;
    private final MangaChapterRepository chapterRepository;
    private final MangaPageRepository pageRepository;
    private final MangaAuthorRepository authorRepository;
    private final TagRepository tagRepository;

    @Value("${app.storage.root:uploads}")
    private String rootUploadDir;

    @GetMapping
    public String mangaList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer tagId,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            Model model) {

        // 1. Lấy tất cả Manga (Nếu dữ liệu lớn nên dùng JPA Specification, ở đây dùng Stream cho đơn giản)
        List<MangaSeries> allMangas = mangaRepository.findAll();
        Stream<MangaSeries> stream = allMangas.stream();

        // 2. Lọc theo Keyword (Tên truyện hoặc Tác giả)
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.toLowerCase();
            stream = stream.filter(m -> m.getTitle().toLowerCase().contains(k)
                    || m.getAuthors().stream().anyMatch(a -> a.getName().toLowerCase().contains(k)));
        }

        // 3. Lọc theo Tag
        if (tagId != null) {
            stream = stream.filter(m -> m.getTags().stream().anyMatch(t -> t.getId().equals(tagId)));
        }

        List<MangaSeries> filtered = stream.collect(Collectors.toList());

        // 4. Sắp xếp
        switch (sort) {
            case "name_asc":
                filtered.sort(Comparator.comparing(MangaSeries::getTitle));
                break;
            case "chapters_desc":
                filtered.sort((a, b) -> Integer.compare(b.getChapters().size(), a.getChapters().size()));
                break;
            case "oldest":
                filtered.sort(Comparator.comparing(MangaSeries::getCreatedAt));
                break;
            case "latest":
            default:
                // Ưu tiên ngày Update, nếu không có thì dùng ngày tạo
                filtered.sort((a, b) -> {
                    // SỬA: Đổi kiểu Date thành LocalDateTime
                    java.time.LocalDateTime dateA = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
                    java.time.LocalDateTime dateB = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();

                    if (dateA == null && dateB == null) {
                        return 0;
                    }
                    if (dateA == null) {
                        return 1;
                    }
                    if (dateB == null) {
                        return -1;
                    }

                    return dateB.compareTo(dateA); // Mới nhất lên đầu
                });
                break;
        }

        // 5. Truyền dữ liệu xuống View
        model.addAttribute("mangas", filtered);
        model.addAttribute("tags", tagRepository.findAll()); // Để hiển thị Dropdown Tag

        // Giữ lại trạng thái bộ lọc trên giao diện
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedTagId", tagId);
        model.addAttribute("selectedSort", sort);

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

                // 1. Lưu ảnh gốc
                Path destFile = coverDir.resolve(coverName);
                cover.transferTo(destFile);
                manga.setCoverPath("/manga/covers/" + coverName);

                // 2. Tạo Thumbnail cho Cover (MỚI)
                try {
                    Path thumbDir = coverDir.resolve(".thumbs");
                    if (!Files.exists(thumbDir)) {
                        Files.createDirectories(thumbDir);
                    }

                    // Tạo thumb 400px
                    createThumbnail(destFile.toFile(), thumbDir.resolve(coverName).toFile(), 400);
                } catch (Exception e) {
                    System.err.println("Failed to create cover thumbnail: " + e.getMessage());
                }
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
        long actualSize = Files.copy(inputStream, destFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Path thumbDir = Paths.get(rootUploadDir, chapter.getFolderPath(), ".thumbs");
        if (!Files.exists(thumbDir)) {
            Files.createDirectories(thumbDir);
        }

        Path destThumb = thumbDir.resolve(fileName);
        try {
            createThumbnail(destFile.toFile(), destThumb.toFile(), 300); // Resize về width 300px
        } catch (Exception e) {
            System.err.println("Failed to create thumbnail for " + fileName + ": " + e.getMessage());
            // Không throw lỗi chết chương trình, chỉ log lại.
        }

        // C. Lưu vào DB
        MangaPage page = MangaPage.builder()
                .chapter(chapter)
                .fileName(fileName)
                .size(actualSize)
                .pageOrder(extractPageNumber(fileName))
                .build();
        pageRepository.save(page);
    }

    private void createThumbnail(File source, File dest, int targetWidth) {
        try {
            Thumbnails.of(source)
                    .size(targetWidth, targetWidth) // Kích thước tối đa (nó tự giữ tỷ lệ)
                    .outputQuality(0.8) // Nén ảnh giảm dung lượng (80% chất lượng)
                    .toFile(dest);
        } catch (IOException e) {
            System.err.println("Thumbnail error: " + e.getMessage());
        }
    }

    @GetMapping("/cover/{mangaId}/thumb")
    @ResponseBody
    public ResponseEntity<Resource> getCoverThumbnail(@PathVariable String mangaId) {
        try {
            MangaSeries manga = mangaRepository.findById(mangaId).orElseThrow();
            if (manga.getCoverPath() == null) {
                return ResponseEntity.notFound().build();
            }

            // Cover Path trong DB dạng: /manga/covers/abc.jpg
            // Cần chuyển thành: {root}/manga/covers/.thumbs/abc.jpg
            String relativePath = manga.getCoverPath().startsWith("/") ? manga.getCoverPath().substring(1) : manga.getCoverPath();
            Path originalFile = Paths.get(rootUploadDir, relativePath);
            Path thumbFile = originalFile.getParent().resolve(".thumbs").resolve(originalFile.getFileName());

            Resource resource = new UrlResource(thumbFile.toUri());
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(resource);
            } else {
                // Nếu chưa có thumb (ảnh cũ), trả về ảnh gốc (fallback)
                Resource originalRes = new UrlResource(originalFile.toUri());
                if (originalRes.exists()) {
                    return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(originalRes);
                }
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/image/{pageId}/thumb")
    @ResponseBody
    public ResponseEntity<Resource> getMangaThumbnail(@PathVariable String pageId) {
        try {
            MangaPage page = pageRepository.findById(pageId).orElseThrow();

            // Đường dẫn đến file thumbnail: {root}/{chapterPath}/.thumbs/{fileName}
            Path thumbPath = Paths.get(rootUploadDir, page.getChapter().getFolderPath(), ".thumbs", page.getFileName());

            Resource resource = new UrlResource(thumbPath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(resource);
            } else {
                // Fallback: Nếu không có thumb (do ảnh cũ), trả về ảnh gốc
                return getMangaImage(pageId);
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
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

        Map<String, String> previewMap = new HashMap<>();
        for (MangaChapter chap : chapters) {
            // Lấy trang đầu tiên (page_order nhỏ nhất)
            // Lưu ý: Để tối ưu hiệu năng nếu dữ liệu lớn, sau này nên dùng Custom Query
            pageRepository.findFirstByChapterIdOrderByPageOrderAsc(chap.getId())
                    .ifPresent(page -> previewMap.put(chap.getId(), page.getId()));
        }

        model.addAttribute("manga", manga);
        model.addAttribute("chapters", chapters);
        model.addAttribute("previewMap", previewMap);
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
                    // coverPath dạng "/manga/covers/abc.jpg" -> cần bỏ dấu "/" đầu
                    String relativeCover = manga.getCoverPath().startsWith("/")
                            ? manga.getCoverPath().substring(1)
                            : manga.getCoverPath();

                    Path coverFile = Paths.get(rootUploadDir, relativeCover);

                    // 1. Xóa ảnh gốc
                    Files.deleteIfExists(coverFile);

                    // 2. Xóa Thumbnail (MỚI)
                    // Thumb nằm ở: .../covers/.thumbs/abc.jpg
                    try {
                        if (coverFile.getParent() != null) {
                            Path thumbFile = coverFile.getParent().resolve(".thumbs").resolve(coverFile.getFileName());
                            Files.deleteIfExists(thumbFile);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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

    // --- 1. API THÊM CHƯƠNG (UPLOAD ZIP) ---
    @PostMapping("/{id}/chapter/add")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> addChapter(
            @PathVariable String id,
            @RequestParam("name") String chapterName,
            @RequestParam("file") MultipartFile zipFile) {
        try {
            MangaSeries manga = mangaRepository.findById(id).orElseThrow();

            // Check trùng tên chương trong truyện này
            if (chapterRepository.findByMangaIdAndChapterName(id, chapterName) != null) {
                return ResponseEntity.badRequest().body("Chapter name already exists!");
            }

            // Tạo Chapter
            MangaChapter chapter = MangaChapter.builder()
                    .manga(manga)
                    .chapterName(chapterName)
                    .folderPath("")
                    .build();
            chapter = chapterRepository.save(chapter);

            // Tạo đường dẫn vật lý
            String relativePath = Paths.get("manga", "content", manga.getId(), chapter.getId()).toString();
            Path absolutePath = Paths.get(rootUploadDir, relativePath);
            Files.createDirectories(absolutePath);

            chapter.setFolderPath(relativePath);
            chapterRepository.save(chapter);

            // Xử lý ZIP (Chỉ lấy ảnh, không quan tâm folder con)
            try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String fileName = new File(entry.getName()).getName(); // Lấy tên file gốc, bỏ qua folder trong zip
                    if (!isImageFile(fileName)) {
                        continue;
                    }

                    saveImage(zis, manga, chapterName, fileName, entry.getSize(), new HashMap<>()); // Tận dụng hàm saveImage cũ
                }
            }

            return ResponseEntity.ok("Chapter added successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/chapters/bulk-add")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> addBulkChapters(
            @PathVariable String id,
            @RequestParam("files") MultipartFile[] files) {

        StringBuilder resultMsg = new StringBuilder();
        int successCount = 0;

        try {
            MangaSeries manga = mangaRepository.findById(id).orElseThrow();

            for (MultipartFile zipFile : files) {
                // Tự động lấy tên Chapter từ tên file (Bỏ đuôi .zip)
                String originalName = zipFile.getOriginalFilename();
                if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
                    resultMsg.append("Skipped non-zip: ").append(originalName).append("\n");
                    continue;
                }

                String chapterName = originalName.substring(0, originalName.lastIndexOf('.'));

                // Check trùng tên
                if (chapterRepository.findByMangaIdAndChapterName(id, chapterName) != null) {
                    resultMsg.append("Skipped exists: ").append(chapterName).append("\n");
                    continue;
                }

                // Gọi hàm xử lý (Tách logic ra hàm riêng bên dưới)
                processChapterZipUpload(manga, chapterName, zipFile);
                successCount++;
            }

            return ResponseEntity.ok("Uploaded " + successCount + " chapters.\n" + resultMsg.toString());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // --- HELPER: Hàm xử lý logic tạo Chapter và giải nén ZIP ---
    private void processChapterZipUpload(MangaSeries manga, String chapterName, MultipartFile zipFile) throws Exception {
        // 1. Tạo Chapter trong DB
        MangaChapter chapter = MangaChapter.builder()
                .manga(manga)
                .chapterName(chapterName)
                .folderPath("")
                .build();
        chapter = chapterRepository.save(chapter);

        // 2. Tạo đường dẫn vật lý
        String relativePath = Paths.get("manga", "content", manga.getId(), chapter.getId()).toString();
        Path absolutePath = Paths.get(rootUploadDir, relativePath);
        if (!Files.exists(absolutePath)) {
            Files.createDirectories(absolutePath);
        }

        chapter.setFolderPath(relativePath);
        chapterRepository.save(chapter);

        // 3. Giải nén và lưu ảnh
        // (Tận dụng lại logic cũ, đảm bảo hàm saveImage có quyền truy cập)
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            // Cache giả để hàm saveImage hoạt động
            Map<String, MangaChapter> singleCache = new HashMap<>();
            singleCache.put(chapterName, chapter);

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String fileName = new File(entry.getName()).getName();
                if (!isImageFile(fileName)) {
                    continue;
                }

                // Gọi hàm saveImage có sẵn của bạn
                saveImage(zis, manga, chapterName, fileName, entry.getSize(), singleCache);
            }
        }
    }

    // --- 2. API XÓA CHƯƠNG ---
    @PostMapping("/chapter/delete")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> deleteChapter(@RequestParam("id") String id,
            @RequestParam(defaultValue = "true") boolean deletePhysical) {
        try {
            MangaChapter chapter = chapterRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Chapter not found"));

            if (deletePhysical) {
                // Xóa thư mục vật lý của chapter: {root}/{manga}/{chapID}
                Path chapterDir = Paths.get(rootUploadDir, chapter.getFolderPath());
                deleteFolderRecursively(chapterDir);
            }
            chapterRepository.delete(chapter);
            return ResponseEntity.ok("Chapter deleted");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // --- 3. API LẤY DỮ LIỆU EDIT (Info + Tags + Cover Candidates) ---
    @GetMapping("/{id}/edit-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEditData(@PathVariable String id) {
        try {
            MangaSeries manga = mangaRepository.findById(id).orElseThrow();
            List<Tag> allTags = tagRepository.findAll();

            // Lấy danh sách ảnh đại diện (trang 1 của mỗi chương) để chọn làm bìa
            List<MangaChapter> chapters = chapterRepository.findByMangaIdOrderByChapterNameAsc(id);
            List<Map<String, String>> potentialCovers = new ArrayList<>();

            for (MangaChapter c : chapters) {
                // Lấy trang đầu tiên của chap
                List<MangaPage> pages = pageRepository.findByChapterIdOrderByPageOrderAsc(c.getId());
                if (!pages.isEmpty()) {
                    Map<String, String> item = new HashMap<>();
                    item.put("chapterName", c.getChapterName());
                    item.put("pageId", pages.get(0).getId()); // Dùng ID trang để load ảnh
                    potentialCovers.add(item);
                }
            }

            List<Integer> currentTagIds = manga.getTags().stream()
                    .map(Tag::getId)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("currentTags", currentTagIds);
            response.put("manga", manga);
            response.put("allTags", allTags);
            // Giả sử MangaSeries có field 'tags' (@ManyToMany), nếu chưa có bạn cần thêm vào Entity
            // response.put("currentTags", manga.getTags().stream().map(Tag::getId).collect(Collectors.toList())); 
            response.put("covers", potentialCovers);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    // --- 4. API CẬP NHẬT MANGA (SỬ DỤNG DTO) ---
    @PostMapping("/update")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> updateManga(@ModelAttribute MangaUpdateDTO dto) {
        try {
            // 1. Tìm Manga
            MangaSeries manga = mangaRepository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Manga not found"));

            // 2. Cập nhật thông tin cơ bản
            manga.setTitle(dto.getTitle());
            manga.setDescription(dto.getDescription());
            manga.setStatus(dto.getStatus());

            // 3. Xử lý Tags (Chỉ xử lý list ID được gửi lên)
            if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
                // JPA sẽ tự lọc ra các Tag có ID nằm trong list này
                List<Tag> selectedTags = tagRepository.findAllById(dto.getTagIds());
                manga.setTags(new HashSet<>(selectedTags));
            } else {
                // Nếu list rỗng (người dùng bỏ tick hết) -> Xóa hết tag
                manga.setTags(new HashSet<>());
            }

            // 4. Xử lý Cover
            // Option 1: Upload File mới
            if ("file".equals(dto.getCoverOption()) && dto.getCoverFile() != null && !dto.getCoverFile().isEmpty()) {
                String coverName = System.currentTimeMillis() + "_" + dto.getCoverFile().getOriginalFilename();

                Path coverDir = Paths.get(rootUploadDir, "manga", "covers");
                if (!Files.exists(coverDir)) {
                    Files.createDirectories(coverDir);
                }

                Path destFile = coverDir.resolve(coverName);
                dto.getCoverFile().transferTo(destFile);

                // Tạo Thumbnail
                try {
                    Path thumbDir = coverDir.resolve(".thumbs");
                    if (!Files.exists(thumbDir)) {
                        Files.createDirectories(thumbDir);
                    }

                    Thumbnails.of(destFile.toFile())
                            .size(400, 600)
                            .outputQuality(0.8)
                            .toFile(thumbDir.resolve(coverName).toFile());
                } catch (Exception e) {
                    System.err.println("Cover thumb error: " + e.getMessage());
                }

                manga.setCoverPath("/manga/covers/" + coverName);
            } // Option 2: Chọn từ Chapter
            else if ("select".equals(dto.getCoverOption()) && dto.getCoverPageId() != null && !dto.getCoverPageId().isEmpty()) {
                MangaPage page = pageRepository.findById(dto.getCoverPageId()).orElseThrow();

                Path sourceImg = Paths.get(rootUploadDir, page.getChapter().getFolderPath(), page.getFileName());
                String newCoverName = "cover_" + manga.getId() + "_" + System.currentTimeMillis() + ".jpg";

                Path coverDir = Paths.get(rootUploadDir, "manga", "covers");
                if (!Files.exists(coverDir)) {
                    Files.createDirectories(coverDir);
                }

                Path destImg = coverDir.resolve(newCoverName);
                Files.copy(sourceImg, destImg, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // Tạo Thumbnail cho ảnh chọn từ chapter
                try {
                    Path thumbDir = coverDir.resolve(".thumbs");
                    if (!Files.exists(thumbDir)) {
                        Files.createDirectories(thumbDir);
                    }

                    Thumbnails.of(destImg.toFile())
                            .size(400, 600)
                            .outputQuality(0.8)
                            .toFile(thumbDir.resolve(newCoverName).toFile());
                } catch (Exception e) {
                    System.err.println("Error creating thumb: " + e.getMessage());
                }

                manga.setCoverPath("/manga/covers/" + newCoverName);
            }

            mangaRepository.save(manga);
            return ResponseEntity.ok("Updated successfully");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/chapter/{id}/details")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getChapterDetails(@PathVariable String id
    ) {
        try {
            MangaChapter chapter = chapterRepository.findById(id).orElseThrow();
            List<MangaPage> pages = pageRepository.findByChapterIdOrderByPageOrderAsc(id);

            Map<String, Object> res = new HashMap<>();
            res.put("id", chapter.getId());
            res.put("name", chapter.getChapterName());
            // Chỉ lấy các trường cần thiết của Page để json nhẹ
            res.put("pages", pages.stream().map(p -> Map.of(
                    "id", p.getId(),
                    "fileName", p.getFileName(),
                    "url", "/manga/image/" + p.getId()
            )).collect(Collectors.toList()));

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // B. Cập nhật tên Chapter
    @PostMapping("/chapter/update-info")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> updateChapterInfo(@RequestParam("id") String id, @RequestParam("name") String name
    ) {
        try {
            MangaChapter chapter = chapterRepository.findById(id).orElseThrow();
            chapter.setChapterName(name);
            chapterRepository.save(chapter);
            return ResponseEntity.ok("Updated");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // C. Sắp xếp lại thứ tự trang (Nhận vào danh sách ID trang đã sắp xếp)
    @PostMapping("/chapter/reorder-pages")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> reorderPages(@RequestBody Map<String, List<String>> payload
    ) {
        try {
            List<String> pageIds = payload.get("pageIds");
            for (int i = 0; i < pageIds.size(); i++) {
                String pageId = pageIds.get(i);
                // Update page_order = index mới
                // Lưu ý: Để tối ưu, nên dùng executeUpdate HQL thay vì find + save từng cái
                pageRepository.updatePageOrder(pageId, i);
            }
            return ResponseEntity.ok("Reordered");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
    // *LƯU Ý: Cần thêm method updatePageOrder vào MangaPageRepository (xem bên dưới)

    // D. Xóa 1 trang ảnh
    @PostMapping("/page/delete")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> deletePage(@RequestParam("id") String id, @RequestParam(defaultValue = "true") boolean deletePhysical
    ) {
        try {
            MangaPage page = pageRepository.findById(id).orElseThrow();
            if (deletePhysical) {
                Path file = Paths.get(rootUploadDir, page.getChapter().getFolderPath(), page.getFileName());
                Files.deleteIfExists(file);
            }
            pageRepository.delete(page);
            return ResponseEntity.ok("Deleted");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // E. Thêm ảnh vào Chapter (Upload thêm)
    @PostMapping("/chapter/{id}/add-images")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> addImagesToChapter(@PathVariable String id, @RequestParam("files") MultipartFile[] files
    ) {
        try {
            MangaChapter chapter = chapterRepository.findById(id).orElseThrow();
            MangaSeries manga = chapter.getManga();

            // Tìm pageOrder lớn nhất hiện tại để cộng dồn
            // Integer maxOrder = pageRepository.findMaxPageOrderByChapterId(id); 
            // int startOrder = (maxOrder == null) ? 0 : maxOrder + 1;
            // Để đơn giản, ta lấy list size
            int startOrder = pageRepository.findByChapterIdOrderByPageOrderAsc(id).size();

            // Cache map rỗng vì ta đang add vào chapter đã xác định
            Map<String, MangaChapter> cache = new HashMap<>();
            cache.put(chapter.getChapterName(), chapter);

            for (MultipartFile file : files) {
                // Tái sử dụng logic saveImage, nhưng cần sửa lại saveImage chút để nhận pageOrder tùy chỉnh
                // Hoặc viết logic lưu thẳng ở đây cho nhanh:
                String fileName = file.getOriginalFilename();
                Path destFile = Paths.get(rootUploadDir, chapter.getFolderPath(), fileName);
                Files.copy(file.getInputStream(), destFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                MangaPage page = MangaPage.builder()
                        .chapter(chapter)
                        .fileName(fileName)
                        .size(file.getSize())
                        .pageOrder(startOrder++) // Tự tăng order
                        .build();
                pageRepository.save(page);
            }
            return ResponseEntity.ok("Added");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
