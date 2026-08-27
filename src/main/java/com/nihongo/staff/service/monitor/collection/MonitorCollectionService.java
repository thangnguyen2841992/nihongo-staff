package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.MonitorMetric;
import com.nihongo.staff.model.monitoring.MonitorObject;

public interface MonitorCollectionService {
    void collectMetric(Long vpsId, MonitorMetric metric);
    void collectObjectMetric(Long vpsId, MonitorMetric metric);
    void collectVpsMetric(Long vpsId, MonitorMetric metric);
    void collect(Long vpsId, MonitorMetric metric, MonitorObject object);
}
