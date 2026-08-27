package com.nihongo.staff.service.monitor.metric;

import com.nihongo.staff.model.monitoring.MonitorMetric;

import java.util.List;

public interface IMonitorMetricService {
    List<MonitorMetric> findAll();
    List<MonitorMetric> findEnabledMetrics();
    MonitorMetric findById(Long metricId);
    MonitorMetric findByCode(String metricCode);
    MonitorMetric create(MonitorMetric metric);
    MonitorMetric update(Long metricId, MonitorMetric request);
    void updateSchedule(Long metricId, Integer scheduleSeconds);
    void updateEnabled(Long metricId, Boolean enabled);
    void delete(Long metricId);
    void validate(MonitorMetric metric);
}
