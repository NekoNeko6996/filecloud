package com.app.filecloud.controller;

import com.app.filecloud.entity.StorageVolume;
import com.app.filecloud.repository.StorageVolumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice(assignableTypes = {
    HomeController.class,               // Dashboard
    SubjectController.class,            // Subject Profile, List
    SocialPlatformController.class,     // Social Platform
    TagController.class,                // Tag
    // Thêm các Controller quản lý khác nếu có (VD: AdminController, TagController...)
    // KHÔNG thêm VideoPageController nếu trang xem video không dùng chung Sidebar này
})
@RequiredArgsConstructor
public class GlobalDataAdvice {

    private final StorageVolumeRepository volumeRepository;

    /**
     * Hàm này sẽ tự động chạy cho mọi Request trả về View.
     * Biến "activeVolumes" sẽ luôn có mặt trong HTML ở bất kỳ trang nào.
     */
    @ModelAttribute("activeVolumes")
    public List<StorageVolume> populateStorageVolumes() {
        // Chỉ lấy các ổ đĩa đang hoạt động
        return volumeRepository.findVolumesInUse();
    }
}