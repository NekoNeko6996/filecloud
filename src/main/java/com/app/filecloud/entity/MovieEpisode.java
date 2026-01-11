package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "movie_episodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovieEpisode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    private String title;

    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;
    
    @Column(name = "thumbnail_path", length = 500)
    private String thumbnailPath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 1 Tập có nhiều phụ đề
    @OneToMany(mappedBy = "episode", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieSubtitle> subtitles = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}