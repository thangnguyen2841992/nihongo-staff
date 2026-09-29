package com.nihongo.staff.service.monitor.vps;

import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsRequest;
import com.nihongo.staff.model.monitoring.dto.NodeExporterDiscoveryResult;
import com.nihongo.staff.model.monitoring.dto.RegisterMonitorVpsRequest;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsResponse;
import java.util.List;

public interface IMonitorVpsService {
    List<MonitorVpsResponse> listVps();
    NodeExporterDiscoveryResult discover(MonitorVpsRequest request);
    MonitorVps registerVps(RegisterMonitorVpsRequest request);
}
