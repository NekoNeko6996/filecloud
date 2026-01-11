package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "movie_subtitles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovieSubtitle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private MovieEpisode episode;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(length = 100)
    private String label;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(length = 10)
    private String format;
}