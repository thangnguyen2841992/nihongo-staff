package com.nihongo.staff.service.monitor.object;

import com.nihongo.staff.model.monitoring.MonitorObject;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.ObjectStatus;
import com.nihongo.staff.repository.MonitorObjectRepository;
import com.nihongo.staff.repository.MonitorVpsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitorObjectServiceImpl  implements MonitorObjectService{
    private final MonitorObjectRepository objectRepository;
    private final MonitorVpsRepository vpsRepository;

    @Transactional(readOnly = true)
    @Override
    public List<MonitorObject> findByVps(
            Long vpsId
    ) {

        return objectRepository
                .findByVps_VpsId(vpsId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonitorObject> findActiveObjects(
            Long vpsId,
            String objectType
    ) {

        return objectRepository
                .findByVps_VpsIdAndObjectTypeAndStatus(
                        vpsId,
                        objectType,
                        ObjectStatus.ACTIVE
                );
    }

    @Override
    @Transactional
    public MonitorObject registerObject(
            Long vpsId,
            String objectType,
            String objectKey
    ) {

        LocalDateTime now = LocalDateTime.now();

        MonitorObject object =
                objectRepository
                        .findByVps_VpsIdAndObjectTypeAndObjectKey(
                                vpsId,
                                objectType,
                                objectKey
                        )
                        .orElse(null);

        if (object == null) {

            MonitorVps vps = vpsRepository
                    .findById(vpsId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "VPS not found: " + vpsId
                            )
                    );

            object = new MonitorObject();

            object.setVps(vps);
            object.setObjectType(objectType);
            object.setObjectKey(objectKey);
            object.setStatus(ObjectStatus.ACTIVE);
            object.setFirstSeenAt(now);

        } else {

            object.setStatus(ObjectStatus.ACTIVE);
        }

        object.setLastSeenAt(now);
        object.setUpdatedAt(now);

        if (object.getCreatedAt() == null) {
            object.setCreatedAt(now);
        }

        return objectRepository.save(object);
    }

    @Override
    @Transactional
    public void markOffline(
            Long vpsId,
            String objectType,
            String objectKey
    ) {

        MonitorObject object =
                objectRepository
                        .findByVps_VpsIdAndObjectTypeAndObjectKey(
                                vpsId,
                                objectType,
                                objectKey
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Object not found"
                                )
                        );

        object.setStatus(ObjectStatus.OFFLINE);
        object.setUpdatedAt(LocalDateTime.now());

        objectRepository.save(object);
    }
}
