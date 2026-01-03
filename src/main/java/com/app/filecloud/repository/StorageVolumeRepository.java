package com.app.filecloud.repository;

import com.app.filecloud.entity.StorageVolume;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StorageVolumeRepository extends JpaRepository<StorageVolume, Integer> {
    Optional<StorageVolume> findByUuid(String uuid);
    Optional<StorageVolume> findByMountPoint(String mountPoint); // Tìm theo ký tự ổ đĩa (F:\)
}