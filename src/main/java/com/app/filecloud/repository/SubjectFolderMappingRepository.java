package com.app.filecloud.repository;

import com.app.filecloud.entity.SubjectFolderMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SubjectFolderMappingRepository extends JpaRepository<SubjectFolderMapping, Integer> {
    Optional<SubjectFolderMapping> findBySubjectIdAndVolumeIdAndRelativePath(Integer subjectId, Integer volumeId, String relativePath);
}