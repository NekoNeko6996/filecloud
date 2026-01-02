package com.app.filecloud.repository;

import com.app.filecloud.entity.FileNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileNodeRepository extends JpaRepository<FileNode, String> {
    
    // Lấy danh sách file/folder trong một thư mục cha (Sắp xếp Folder lên trước)
    // Nếu parentId là NULL thì tìm root
    List<FileNode> findByParentIdOrderByTypeDescNameAsc(String parentId);
    
    // Tìm root folder (parent_id is null)
    List<FileNode> findByParentIdIsNullOrderByTypeDescNameAsc();
}

