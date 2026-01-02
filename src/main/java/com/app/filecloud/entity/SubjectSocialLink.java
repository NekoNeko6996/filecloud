package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subject_social_links")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectSocialLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "subject_id")
    private ContentSubject subject;

    @ManyToOne
    @JoinColumn(name = "platform_id")
    private SocialPlatform platform;

    @Column(name = "profile_path")
    private String profilePath;
    
    @Column(name = "full_url_override")
    private String fullUrlOverride;
}