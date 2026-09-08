package com.nihongo.staff.service.monitor.vps;

import com.nihongo.staff.model.monitoring.MonitorPrometheusTarget;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsRequest;
import com.nihongo.staff.model.monitoring.dto.NodeExporterDiscoveryResult;

public interface IMonitorVpsService {
    NodeExporterDiscoveryResult discover(MonitorVpsRequest request);

    MonitorPrometheusTarget registerTarget(MonitorVps vps);

    void enableTarget(Long targetId);
    void disableTarget(Long targetId);
    void deleteTarget(Long targetId);
}
