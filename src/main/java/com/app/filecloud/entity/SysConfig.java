package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysConfig {

    @Id
    @Column(name = "config_key", nullable = false, length = 50)
    private String key; // Ví dụ: "UPLOAD_MAX_SIZE"

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String value;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private DataType dataType; // STRING, INT, BOOL, JSON

    @Column(name = "is_system")
    private Boolean isSystem = false;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum DataType {
        STRING, INT, BOOL, JSON
    }
}