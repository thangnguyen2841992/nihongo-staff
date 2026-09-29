package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorObject;
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



    // Current reads are required after the VPS mutex under MySQL REPEATABLE READ.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from MonitorObject o where o.vps.vpsId = :vpsId and o.objectType = :type and o.objectKey = :key")
    Optional<MonitorObject> findForUpdate(Long vpsId, String type, String key);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from MonitorObject o where o.vps.vpsId = :vpsId and o.objectType = :type")
    List<MonitorObject> findTypeForUpdate(Long vpsId, String type);

}
