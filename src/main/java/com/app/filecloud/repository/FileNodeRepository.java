package com.app.filecloud.repository;

import com.app.filecloud.entity.FileNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface FileNodeRepository extends JpaRepository<FileNode, String> {
    
    // Lấy danh sách file/folder trong một thư mục cha (Sắp xếp Folder lên trước)
    // Nếu parentId là NULL thì tìm root
    List<FileNode> findByParentIdOrderByTypeDescNameAsc(String parentId);
    
    // Tìm root folder (parent_id is null)
    List<FileNode> findByParentIdIsNullOrderByTypeDescNameAsc();
    
    Optional<FileNode> findByRelativePath(String relativePath);
    
    Optional<FileNode> findByVolumeIdAndRelativePath(Integer volumeId, String relativePath);
    
    @Query("SELECT f FROM FileNode f JOIN FileSubject fs ON f.id = fs.fileId WHERE fs.subjectId = :subjectId ORDER BY f.createdAt DESC")
    List<FileNode> findBySubjectId(Integer subjectId);
}

