package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "storage_volumes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageVolume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String label; // Tên ổ đĩa (VD: DATA_DISK)

    @Column(name = "mount_point")
    private String mountPoint; // Đường dẫn gắn ổ (VD: F:\, /mnt/data)

    @Column(unique = true)
    private String uuid; // Serial Number của ổ cứng

    @Column(name = "total_capacity")
    private Long totalCapacity;

    @Column(name = "available_capacity")
    private Long availableCapacity;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "last_scanned_at")
    private LocalDateTime lastScannedAt;

    public enum Status { ONLINE, OFFLINE, MAINTENANCE }
}