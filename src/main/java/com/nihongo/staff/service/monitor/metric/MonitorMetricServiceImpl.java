package com.nihongo.staff.service.monitor.metric;

import com.nihongo.staff.model.monitoring.MonitorMetric;
import com.nihongo.staff.repository.MonitorMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonitorMetricServiceImpl implements IMonitorMetricService {

    private final MonitorMetricRepository metricRepository;

    @Override
    public List<MonitorMetric> findAll() {
        return metricRepository.findAll();
    }

    @Override
    public List<MonitorMetric> findEnabledMetrics() {
        return metricRepository
                .findByEnabledTrueOrderByMetricNameAsc();
    }

    @Override
    public MonitorMetric findById(Long metricId) {

        return metricRepository.findById(metricId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Metric not found: " + metricId
                        )
                );
    }

    @Override
    public MonitorMetric findByCode(String metricCode) {

        return metricRepository
                .findByMetricCode(metricCode)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Metric not found: " + metricCode
                        )
                );
    }

    @Override
    @Transactional
    public MonitorMetric create(MonitorMetric metric) {

        if (metricRepository.existsByMetricCode(
                metric.getMetricCode())) {

            throw new RuntimeException(
                    "Metric code already exists: "
                            + metric.getMetricCode()
            );
        }

        validate(metric);

        LocalDateTime now = LocalDateTime.now();

        metric.setCreatedAt(now);
        metric.setUpdatedAt(now);

        return metricRepository.save(metric);
    }

    @Override
    @Transactional
    public MonitorMetric update(
            Long metricId,
            MonitorMetric request
    ) {

        MonitorMetric metric = findById(metricId);

        validate(request);

        metric.setMetricName(request.getMetricName());
        metric.setDescription(request.getDescription());

        metric.setObjectLevelYn(
                request.getObjectLevelYn()
        );

        metric.setObjectType(
                request.getObjectType()
        );

        metric.setUnit(request.getUnit());

        metric.setValueType(
                request.getValueType()
        );

        metric.setCollectorType(
                request.getCollectorType()
        );

        metric.setTimeoutMs(
                request.getTimeoutMs()
        );

        metric.setScheduleSeconds(
                request.getScheduleSeconds()
        );

        metric.setEnabled(
                request.getEnabled()
        );

        metric.setUpdatedAt(LocalDateTime.now());

        return metricRepository.save(metric);
    }

    @Override
    @Transactional
    public void updateSchedule(
            Long metricId,
            Integer scheduleSeconds
    ) {

        if (scheduleSeconds == null ||
                scheduleSeconds <= 0) {

            throw new IllegalArgumentException(
                    "scheduleSeconds must be greater than 0"
            );
        }

        MonitorMetric metric = findById(metricId);

        metric.setScheduleSeconds(scheduleSeconds);
        metric.setUpdatedAt(LocalDateTime.now());

        metricRepository.save(metric);
    }

    @Override
    @Transactional
    public void updateEnabled(
            Long metricId,
            Boolean enabled
    ) {

        MonitorMetric metric = findById(metricId);

        metric.setEnabled(enabled);
        metric.setUpdatedAt(LocalDateTime.now());

        metricRepository.save(metric);
    }

    @Override
    @Transactional
    public void delete(Long metricId) {

        MonitorMetric metric = findById(metricId);

        metricRepository.delete(metric);
    }

    @Override
    public void validate(MonitorMetric metric) {

        if (metric.getObjectLevelYn() == null) {
            metric.setObjectLevelYn(false);
        }

        if (Boolean.TRUE.equals(
                metric.getObjectLevelYn())) {

            if (metric.getObjectType() == null ||
                    metric.getObjectType().isBlank()) {

                throw new IllegalArgumentException(
                        "objectType is required when objectLevelYn = true"
                );
            }

        } else {

            /*
             * Metric không có object
             * thì objectType phải NULL.
             */
            metric.setObjectType(null);
        }

        if (metric.getScheduleSeconds() == null ||
                metric.getScheduleSeconds() <= 0) {

            throw new IllegalArgumentException(
                    "scheduleSeconds must be greater than 0"
            );
        }

        if (metric.getTimeoutMs() == null ||
                metric.getTimeoutMs() <= 0) {

            metric.setTimeoutMs(5000);
        }

        if (metric.getCollectorType() == null ||
                metric.getCollectorType().isBlank()) {

            throw new IllegalArgumentException(
                    "collectorType is required"
            );
        }
    }
}