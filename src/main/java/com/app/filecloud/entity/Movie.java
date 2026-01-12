package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(name = "original_title")
    private String originalTitle;
    
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<MovieAlternativeTitle> alternativeTitles = new HashSet<>();

    // [NEW] Quan hệ với bảng TAGS có sẵn
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "movie_tags",
        joinColumns = @JoinColumn(name = "movie_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id") // Tag dùng Integer ID
    )
    @Builder.Default
    private Set<Tag> tags = new java.util.HashSet<>();

    // [NEW] Quan hệ với bảng STUDIOS mới
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "movie_studios",
        joinColumns = @JoinColumn(name = "movie_id"),
        inverseJoinColumns = @JoinColumn(name = "studio_id")
    )
    @Builder.Default
    private Set<Studio> studios = new java.util.HashSet<>();

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;
    
    @Column(name = "thumbnail_path", length = 500)  
    private String thumbnailPath;

    private Double rating;

    @Column(name = "tmdb_id", length = 50)
    private String tmdbId;

    @Column(name = "imdb_id", length = 50)
    private String imdbId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // --- RELATIONSHIPS ---

    // 1 Phim có nhiều tập
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MovieEpisode> episodes = new ArrayList<>();

    // 1 Phim có nhiều Credits (Diễn viên/Đạo diễn)
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MovieCredit> credits = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}