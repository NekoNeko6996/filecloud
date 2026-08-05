package com.app.filecloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GhostDeleteRequest {

    private List<DeleteItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteItem {
        private String id;
        private String itemType; // "FILE_NODE" or "GALLERY_PHOTO"
    }
}
