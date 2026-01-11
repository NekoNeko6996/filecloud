package com.app.filecloud.repository;

import com.app.filecloud.entity.SysConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysConfigRepository extends JpaRepository<SysConfig, String> {
    Optional<SysConfig> findByKey(String key);
}