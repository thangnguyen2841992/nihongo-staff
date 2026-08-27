package com.nihongo.staff.service.monitor.vps;

import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.VpsStatus;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsRequest;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsResponse;

import java.util.List;

public interface IMonitorVpsService {
    MonitorVpsResponse register(MonitorVpsRequest request);
}
