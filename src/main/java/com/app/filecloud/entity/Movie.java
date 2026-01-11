package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(name = "original_title")
    private String originalTitle;

    private String studio;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    private Float rating;

    @Column(name = "tmdb_id", length = 50)
    private String tmdbId;

    @Column(name = "imdb_id", length = 50)
    private String imdbId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // --- RELATIONSHIPS ---

    // 1 Phim có nhiều tên khác
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieAlternativeTitle> alternativeTitles = new ArrayList<>();

    // 1 Phim có nhiều tập
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieEpisode> episodes = new ArrayList<>();

    // 1 Phim có nhiều Credits (Diễn viên/Đạo diễn)
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieCredit> credits = new ArrayList<>();

    // 1 Phim có nhiều Tag (Many-to-Many với bảng Tags có sẵn)
    @ManyToMany
    @JoinTable(
        name = "movie_tags",
        joinColumns = @JoinColumn(name = "movie_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags = new ArrayList<>(); // Giả sử bạn đã có Entity Tag

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