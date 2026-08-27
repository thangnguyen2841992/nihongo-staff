package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorObject;
import com.nihongo.staff.model.monitoring.ObjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonitorObjectRepository
        extends JpaRepository<MonitorObject, Long> {

    List<MonitorObject> findByVps_VpsId(Long vpsId);

    List<MonitorObject> findByVps_VpsIdAndObjectType(
            Long vpsId,
            String objectType
    );

    List<MonitorObject> findByVps_VpsIdAndObjectTypeAndStatus(
            Long vpsId,
            String objectType,
            ObjectStatus status
    );

    Optional<MonitorObject> findByVps_VpsIdAndObjectTypeAndObjectKey(
            Long vpsId,
            String objectType,
            String objectKey
    );

    boolean existsByVps_VpsIdAndObjectTypeAndObjectKey(
            Long vpsId,
            String objectType,
            String objectKey
    );
}