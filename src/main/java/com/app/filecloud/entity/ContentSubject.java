package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "content_subjects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "main_name", nullable = false)
    private String mainName;

    @Column(name = "alias_name_1")
    private String aliasName1;

    @Column(name = "alias_name_2")
    private String aliasName2;

    @Column(name = "avatar_url")
    private String avatarUrl; // Link tới ảnh đại diện

    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "subject", fetch = FetchType.LAZY)
    private java.util.List<SubjectSocialLink> socialLinks;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}