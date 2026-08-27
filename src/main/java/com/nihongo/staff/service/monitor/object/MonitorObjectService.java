package com.nihongo.staff.service.monitor.object;

import com.nihongo.staff.model.monitoring.MonitorObject;

import java.util.List;

public interface MonitorObjectService {
    List<MonitorObject> findByVps(Long vpsId);
    List<MonitorObject> findActiveObjects(Long vpsId, String objectType);
    MonitorObject registerObject(Long vpsId, String objectType, String objectKey);
    void markOffline(Long vpsId, String objectType, String objectKey);

}
