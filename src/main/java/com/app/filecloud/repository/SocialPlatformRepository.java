package com.app.filecloud.repository;

import com.app.filecloud.entity.SocialPlatform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialPlatformRepository extends JpaRepository<SocialPlatform, Integer> {
}