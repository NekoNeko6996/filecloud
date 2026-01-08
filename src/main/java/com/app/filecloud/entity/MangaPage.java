package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "manga_pages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MangaPage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private MangaChapter chapter;

    private String fileName; // 01.jpg

    private int pageOrder; // 1, 2, 3...

    private long size;
}