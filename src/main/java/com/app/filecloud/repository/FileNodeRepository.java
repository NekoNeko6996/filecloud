package com.app.filecloud.repository;

import com.app.filecloud.entity.FileNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    
    @Query("SELECT f FROM FileNode f JOIN FileSubject fs ON f.id = fs.fileId WHERE fs.subjectId = :subjectId AND f.size = :size")
    List<FileNode> findBySubjectIdAndSize(Integer subjectId, Long size);
    
    List<FileNode> findByType(FileNode.Type type, Pageable pageable);
    
    @Query(value = "SELECT f.* FROM file_nodes f " +
                   "JOIN file_tags ft ON f.id = ft.file_id " +
                   "WHERE ft.tag_id = :tagId " +
                   "ORDER BY ft.created_at DESC LIMIT 50", nativeQuery = true)
    List<FileNode> findByTagId(@Param("tagId") Integer tagId);
}

