package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "movie_alternative_titles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovieAlternativeTitle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "alt_title", nullable = false)
    private String altTitle;

    @Column(name = "language_code", length = 10)
    private String languageCode;
}