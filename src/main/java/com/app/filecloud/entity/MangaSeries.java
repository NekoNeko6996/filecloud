package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "manga_series")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MangaSeries {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String coverPath; // Đường dẫn ảnh bìa

    @Enumerated(EnumType.STRING)
    private Status status; // ONGOING, COMPLETED...

    @Column(name = "release_year")
    private String releaseYear;

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinTable(
        name = "manga_series_authors",
        joinColumns = @JoinColumn(name = "manga_id"),
        inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    private Set<MangaAuthor> authors;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public enum Status { ONGOING, COMPLETED, HIATUS, DROPPED }
}