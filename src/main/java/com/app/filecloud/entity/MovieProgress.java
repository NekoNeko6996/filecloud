package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "movie_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovieProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Giả sử bạn đã có Entity User từ hệ thống cũ

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private MovieEpisode episode;

    @Column(name = "stopped_at_seconds")
    private Integer stoppedAtSeconds;

    @Column(name = "is_finished")
    private Boolean isFinished;

    @Column(name = "last_watched_at")
    private LocalDateTime lastWatchedAt;
}