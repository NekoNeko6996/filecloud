package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "file_subjects")
@IdClass(FileSubjectId.class) // Chỉ định class xử lý khóa chính phức hợp
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileSubject {

    @Id
    @Column(name = "file_id")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String fileId;

    @Id
    @Column(name = "subject_id")
    private Integer subjectId;

    @Column(name = "is_main_owner")
    private Boolean isMainOwner;
}