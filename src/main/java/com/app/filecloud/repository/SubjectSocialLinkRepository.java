package com.app.filecloud.repository;

import com.app.filecloud.entity.SubjectSocialLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubjectSocialLinkRepository extends JpaRepository<SubjectSocialLink, Long> {
    List<SubjectSocialLink> findBySubjectId(Integer subjectId);
    
    // THÊM HÀM NÀY: Tìm các subject đang dùng platform này
    List<SubjectSocialLink> findByPlatformId(Integer platformId);
    
    long countByPlatformId(Integer platformId);
}