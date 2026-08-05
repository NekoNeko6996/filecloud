package com.app.filecloud.service;

import com.app.filecloud.dto.GhostDeleteRequest;
import com.app.filecloud.dto.GhostItemDto;
import com.app.filecloud.dto.GhostScanResultDto;
import com.app.filecloud.dto.GhostTreeNodeDto;
import com.app.filecloud.entity.FileNode;
import com.app.filecloud.entity.FileThumbnail;
import com.app.filecloud.entity.GalleryPhoto;
import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.repository.FileNodeRepository;
import com.app.filecloud.repository.FileThumbnailRepository;
import com.app.filecloud.repository.GalleryPhotoRepository;
import com.app.filecloud.repository.StorageVolumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriveCheckService {

    private final StorageVolumeRepository volumeRepository;
    private final FileNodeRepository fileNodeRepository;
    private final GalleryPhotoRepository galleryPhotoRepository;
    private final FileThumbnailRepository fileThumbnailRepository;

    @Value("${app.storage.root:uploads}")
    private String rootUploadDir;

    public List<StorageVolume> getAllVolumes() {
        return volumeRepository.findAll();
    }

    public GhostScanResultDto scanGhostFiles(Integer volumeId) {
        StorageVolume volume = volumeRepository.findById(volumeId)
                .orElseThrow(() -> new IllegalArgumentException("Storage Volume ID " + volumeId + " not found!"));

        String mountPointStr = volume.getMountPoint();
        Path mountPointPath = Paths.get(mountPointStr);
        boolean isOnline = Files.exists(mountPointPath);

        List<GhostItemDto> ghostItems = new ArrayList<>();
        long totalDbFiles = 0;
        long totalGhostSize = 0;

        // 1. Scan FileNode records
        List<FileNode> fileNodes = fileNodeRepository.findByVolumeId(volumeId);
        totalDbFiles += fileNodes.stream().filter(n -> n.getType() == FileNode.Type.FILE).count();

        for (FileNode node : fileNodes) {
            if (node.getType() != FileNode.Type.FILE) {
                continue;
            }

            Path fullPath = Paths.get(mountPointStr, node.getRelativePath() != null ? node.getRelativePath() : "");
            if (!Files.exists(fullPath)) {
                totalGhostSize += node.getSize();
                ghostItems.add(GhostItemDto.builder()
                        .id(node.getId())
                        .itemType("FILE_NODE")
                        .name(node.getName())
                        .relativePath(node.getRelativePath())
                        .fullPath(fullPath.toString())
                        .size(node.getSize())
                        .formattedSize(formatBytes(node.getSize()))
                        .mimeType(node.getMimeType())
                        .createdAt(node.getCreatedAt())
                        .build());
            }
        }

        // 2. Scan GalleryPhoto records
        List<GalleryPhoto> galleryPhotos = galleryPhotoRepository.findByVolumeId(volumeId);
        totalDbFiles += galleryPhotos.size();

        for (GalleryPhoto photo : galleryPhotos) {
            Path fullPath = Paths.get(mountPointStr, photo.getStoragePath() != null ? photo.getStoragePath() : "");
            if (!Files.exists(fullPath)) {
                totalGhostSize += photo.getSize();
                ghostItems.add(GhostItemDto.builder()
                        .id(photo.getId())
                        .itemType("GALLERY_PHOTO")
                        .name(photo.getOriginalFilename())
                        .relativePath(photo.getStoragePath())
                        .fullPath(fullPath.toString())
                        .size(photo.getSize())
                        .formattedSize(formatBytes(photo.getSize()))
                        .mimeType(photo.getMimeType())
                        .createdAt(photo.getCreatedAt())
                        .build());
            }
        }

        // 3. Build Directory Tree Hierarchy
        List<GhostTreeNodeDto> ghostTree = buildGhostTree(ghostItems);

        return GhostScanResultDto.builder()
                .volumeId(volume.getId())
                .volumeLabel(volume.getLabel())
                .mountPoint(volume.getMountPoint())
                .isOnline(isOnline)
                .totalDbFiles(totalDbFiles)
                .ghostCount(ghostItems.size())
                .totalGhostSize(totalGhostSize)
                .formattedGhostSize(formatBytes(totalGhostSize))
                .ghostItems(ghostItems)
                .ghostTree(ghostTree)
                .build();
    }

    @Transactional
    public int deleteGhostItems(List<GhostDeleteRequest.DeleteItem> deleteItems) {
        if (deleteItems == null || deleteItems.isEmpty()) {
            return 0;
        }

        List<String> fileNodeIds = deleteItems.stream()
                .filter(i -> "FILE_NODE".equalsIgnoreCase(i.getItemType()))
                .map(GhostDeleteRequest.DeleteItem::getId)
                .distinct()
                .toList();

        List<String> galleryPhotoIds = deleteItems.stream()
                .filter(i -> "GALLERY_PHOTO".equalsIgnoreCase(i.getItemType()))
                .map(GhostDeleteRequest.DeleteItem::getId)
                .distinct()
                .toList();

        int deletedCount = 0;

        // 1. Purge FileNode ghost items & associated thumbnails/temp frames
        if (!fileNodeIds.isEmpty()) {
            List<FileNode> fileNodes = fileNodeRepository.findAllById(fileNodeIds);

            Path thumbDir = Paths.get(rootUploadDir, ".cache", "thumbnails");
            Path tempDir = Paths.get(rootUploadDir, ".cache", "temp_frames");

            for (FileNode node : fileNodes) {
                String fileId = node.getId();

                // A. Delete physical thumbnail files from disk (.cache/thumbnails and .cache/temp_frames)
                try {
                    Files.deleteIfExists(thumbDir.resolve(fileId + "_small.jpg"));
                    Files.deleteIfExists(thumbDir.resolve(fileId + "_medium.jpg"));
                    Files.deleteIfExists(thumbDir.resolve(fileId + "_large.jpg"));
                    Files.deleteIfExists(tempDir.resolve(fileId + "_source.jpg"));
                } catch (Exception e) {
                    log.warn("Warning deleting physical thumbnail files for fileId {}: {}", fileId, e.getMessage());
                }

                // B. Delete DB thumbnail records & storage paths
                try {
                    List<FileThumbnail> thumbs = fileThumbnailRepository.findByFileId(fileId);
                    for (FileThumbnail t : thumbs) {
                        if (t.getStoragePath() != null) {
                            try {
                                Files.deleteIfExists(Paths.get(rootUploadDir, t.getStoragePath()));
                            } catch (Exception ex) {
                                // ignore if missing
                            }
                        }
                    }
                    if (!thumbs.isEmpty()) {
                        fileThumbnailRepository.deleteAllInBatch(thumbs);
                    }
                } catch (Exception e) {
                    log.warn("Warning cleaning file_thumbnails records for fileId {}: {}", fileId, e.getMessage());
                }
            }

            // DB CASCADE will automatically delete file_subjects, file_tags, media_metadata
            fileNodeRepository.deleteAllInBatch(fileNodes);
            deletedCount += fileNodes.size();
            log.info("Purged {} ghost FileNode records & physical thumbnails.", fileNodes.size());
        }

        // 2. Purge GalleryPhoto ghost items & associated thumbnails
        if (!galleryPhotoIds.isEmpty()) {
            List<GalleryPhoto> galleryPhotos = galleryPhotoRepository.findAllById(galleryPhotoIds);

            for (GalleryPhoto photo : galleryPhotos) {
                try {
                    StorageVolume vol = volumeRepository.findById(photo.getVolumeId()).orElse(null);
                    if (vol != null && photo.getStoragePath() != null) {
                        Path volumeRoot = Paths.get(vol.getMountPoint());
                        Path filePath = volumeRoot.resolve(photo.getStoragePath());
                        if (filePath.getParent() != null && filePath.getFileName() != null) {
                            Path thumbPath = filePath.getParent().resolve(".thumbs").resolve(filePath.getFileName());
                            Files.deleteIfExists(thumbPath);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Warning deleting gallery thumbnail for photoId {}: {}", photo.getId(), e.getMessage());
                }
            }

            // DB CASCADE will automatically delete gallery_photo_metadata, gallery_deep_zoom
            galleryPhotoRepository.deleteAllInBatch(galleryPhotos);
            deletedCount += galleryPhotos.size();
            log.info("Purged {} ghost GalleryPhoto records & thumbnails.", galleryPhotos.size());
        }

        return deletedCount;
    }

    private List<GhostTreeNodeDto> buildGhostTree(List<GhostItemDto> ghostItems) {
        GhostTreeNodeDto rootFolder = GhostTreeNodeDto.builder()
                .id("ROOT")
                .name("Root")
                .relativePath("")
                .nodeType("FOLDER")
                .ghostCount(0)
                .size(0)
                .children(new ArrayList<>())
                .build();

        for (GhostItemDto item : ghostItems) {
            String path = item.getRelativePath();
            if (path == null) path = "";
            path = path.replace('\\', '/');
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            String[] parts = path.split("/");
            GhostTreeNodeDto currentDir = rootFolder;

            for (int i = 0; i < parts.length - 1; i++) {
                String folderName = parts[i];
                if (folderName.isEmpty()) continue;

                String currentRelPath = (currentDir == rootFolder || currentDir.getRelativePath().isEmpty())
                        ? folderName
                        : currentDir.getRelativePath() + "/" + folderName;

                GhostTreeNodeDto nextDir = null;
                for (GhostTreeNodeDto child : currentDir.getChildren()) {
                    if ("FOLDER".equals(child.getNodeType()) && child.getName().equalsIgnoreCase(folderName)) {
                        nextDir = child;
                        break;
                    }
                }

                if (nextDir == null) {
                    nextDir = GhostTreeNodeDto.builder()
                            .id(currentRelPath)
                            .name(folderName)
                            .relativePath(currentRelPath)
                            .nodeType("FOLDER")
                            .ghostCount(0)
                            .size(0)
                            .children(new ArrayList<>())
                            .build();
                    currentDir.getChildren().add(nextDir);
                }

                currentDir = nextDir;
            }

            String fileName = parts[parts.length - 1];
            GhostTreeNodeDto fileNode = GhostTreeNodeDto.builder()
                    .id(item.getId())
                    .name(fileName)
                    .relativePath(item.getRelativePath())
                    .nodeType("FILE")
                    .itemType(item.getItemType())
                    .size(item.getSize())
                    .formattedSize(item.getFormattedSize())
                    .mimeType(item.getMimeType())
                    .createdAt(item.getCreatedAt())
                    .ghostCount(1)
                    .children(new ArrayList<>())
                    .build();

            currentDir.getChildren().add(fileNode);
        }

        calculateFolderStats(rootFolder);
        return rootFolder.getChildren();
    }

    private void calculateFolderStats(GhostTreeNodeDto node) {
        if (!"FOLDER".equals(node.getNodeType())) {
            return;
        }

        int count = 0;
        long totalSize = 0;

        for (GhostTreeNodeDto child : node.getChildren()) {
            if ("FOLDER".equals(child.getNodeType())) {
                calculateFolderStats(child);
                count += child.getGhostCount();
                totalSize += child.getSize();
            } else {
                count += 1;
                totalSize += child.getSize();
            }
        }

        node.setGhostCount(count);
        node.setSize(totalSize);
        node.setFormattedSize(formatBytes(totalSize));

        node.getChildren().sort((a, b) -> {
            boolean aIsFolder = "FOLDER".equals(a.getNodeType());
            boolean bIsFolder = "FOLDER".equals(b.getNodeType());
            if (aIsFolder != bIsFolder) {
                return aIsFolder ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 MB";
        double sizeInMb = bytes / (1024.0 * 1024.0);
        if (sizeInMb < 1024) {
            return new DecimalFormat("#.##").format(sizeInMb) + " MB";
        }
        double sizeInGb = sizeInMb / 1024.0;
        return new DecimalFormat("#.##").format(sizeInGb) + " GB";
    }
}
