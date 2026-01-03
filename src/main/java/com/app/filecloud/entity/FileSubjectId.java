package com.app.filecloud.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileSubjectId implements Serializable {
    private String fileId;
    private Integer subjectId;
}