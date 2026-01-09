package com.app.filecloud.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "manga_chapters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MangaChapter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manga_id")
    @JsonIgnore
    private MangaSeries manga;

    @Column(name = "chapter_name")
    private String chapterName;

    // Lưu đường dẫn TƯƠNG ĐỐI so với rootUploadDir
    // VD: /manga/content/{manga_id}/{chapter_id}/
    @Column(name = "folder_path")
    private String folderPath;

    // --- ĐÃ XÓA TRƯỜNG VOLUME_ID --- 
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
