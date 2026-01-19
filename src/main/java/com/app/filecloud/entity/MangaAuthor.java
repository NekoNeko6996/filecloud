package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "manga_authors")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MangaAuthor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    private String biography;

    // Nếu muốn hiển thị ảnh tác giả
    // private String avatarUrl;
}