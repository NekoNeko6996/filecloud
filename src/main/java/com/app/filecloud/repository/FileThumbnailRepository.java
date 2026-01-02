package com.app.filecloud.repository;

import com.app.filecloud.entity.FileThumbnail;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;


public interface FileThumbnailRepository extends JpaRepository<FileThumbnail, String>{
    Optional<FileThumbnail> findByFileIdAndType(String fileId, FileThumbnail.ThumbType type);
}
