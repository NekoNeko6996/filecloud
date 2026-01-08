package com.app.filecloud.repository;

import com.app.filecloud.entity.StorageVolume;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

public interface StorageVolumeRepository extends JpaRepository<StorageVolume, Integer> {
    Optional<StorageVolume> findByUuid(String uuid);
    Optional<StorageVolume> findByMountPoint(String mountPoint); // Tìm theo ký tự ổ đĩa (F:\)
    
    @Query("SELECT DISTINCT v FROM SubjectFolderMapping m JOIN m.volume v")
    List<StorageVolume> findVolumesInUse();
}