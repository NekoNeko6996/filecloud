package com.app.filecloud.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "media_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaMetadata {

    @Id
    @Column(name = "file_id")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String fileId; // Khóa chính cũng là FK trỏ tới FileNode

    private Integer width;
    private Integer height;
    private Integer orientation;
    
    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    // --- CÁC TRƯỜNG VIDEO (MỚI THÊM) ---
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "video_codec", length = 20)
    private String videoCodec;

    @Column(name = "frame_rate")
    private Double frameRate;
    // ------------------------------------
    
    @Column(name = "gps_lat")
    private Double gpsLat;
    
    @Column(name = "gps_lng")
    private Double gpsLng;
    
    @Column(name = "location_name")
    private String locationName;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", insertable = false, updatable = false)
    @ToString.Exclude // Tránh vòng lặp vô tận khi in log
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private FileNode fileNode;
    
    
    @Transient
    @Builder.Default
    private boolean isNew = true;

    public String getId() {
        return fileId;
    }

    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}