package com.nihongo.staff.service.monitor.vps;

import com.nihongo.staff.model.monitoring.MonitorPrometheusTarget;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsRequest;
import com.nihongo.staff.model.monitoring.dto.NodeExporterDiscoveryResult;
import com.nihongo.staff.model.monitoring.dto.RegisterMonitorVpsRequest;

public interface IMonitorVpsService {
    java.util.List<com.nihongo.staff.model.monitoring.dto.MonitorVpsResponse> listVps();
    NodeExporterDiscoveryResult discover(MonitorVpsRequest request);
    MonitorVps registerVps(RegisterMonitorVpsRequest request);
    MonitorPrometheusTarget registerTarget(Long vpsId);

    void enableTarget(Long targetId);
    void disableTarget(Long targetId);
    void deleteTarget(Long targetId);
}
