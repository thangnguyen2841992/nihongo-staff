package com.nihongo.staff.model.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "monitor_object",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_vps_object",
                        columnNames = {
                                "vps_id",
                                "object_type",
                                "object_key"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_object_vps_type",
                        columnList = "vps_id, object_type, status"
                )
        }
)
@Getter
@Setter
public class MonitorObject extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "object_id")
    private Long objectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "vps_id",
            nullable = false
    )
    private MonitorVps vps;


    @Column(
            name = "object_type",
            nullable = false,
            length = 50
    )
    private String objectType;

    @Column(
            name = "object_key",
            nullable = false,
            length = 255
    )
    private String objectKey;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private ObjectStatus status = ObjectStatus.ACTIVE;


    @Column(
            name = "first_seen_at",
            nullable = false
    )
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}