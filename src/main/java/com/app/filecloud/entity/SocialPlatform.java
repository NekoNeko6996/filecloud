package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "social_platforms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialPlatform {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name; // VD: Pixiv, Twitter
    
    @Column(name = "base_url")
    private String baseUrl;
    
    @Column(name = "icon_url")
    private String iconUrl;
    
    @Column(name = "is_active")
    private boolean isAcvite;
    
    @Transient
    private long usageCount;
}