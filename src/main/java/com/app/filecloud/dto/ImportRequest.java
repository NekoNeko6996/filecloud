package com.app.filecloud.dto;

import java.util.List;
import lombok.Data;


@Data
public class ImportRequest {
    private List<String> paths;
    private String resolution; // "SKIP", "RENAME", "OVERWRITE" (cho file trùng)
}