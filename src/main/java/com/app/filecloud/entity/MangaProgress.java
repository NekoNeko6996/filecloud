package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "manga_progress", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "manga_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MangaProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manga_id", nullable = false)
    private MangaSeries manga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private MangaChapter chapter;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        readAt = LocalDateTime.now();
    }
}
