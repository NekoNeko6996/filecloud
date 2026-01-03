package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subject_folder_mappings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectFolderMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "subject_id")
    private ContentSubject subject;

    @ManyToOne
    @JoinColumn(name = "volume_id")
    private StorageVolume volume;

    @Column(name = "relative_path", columnDefinition = "TEXT")
    private String relativePath; // Đường dẫn từ gốc ổ đĩa (VD: \VIDEO\[Name])

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}