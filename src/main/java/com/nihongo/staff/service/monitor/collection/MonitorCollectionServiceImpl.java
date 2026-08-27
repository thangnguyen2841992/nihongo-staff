package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.MonitorMetric;
import com.nihongo.staff.model.monitoring.MonitorObject;
import com.nihongo.staff.repository.MonitorMetricRepository;
import com.nihongo.staff.service.monitor.object.MonitorObjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class MonitorCollectionServiceImpl implements  MonitorCollectionService {
    private final MonitorMetricRepository metricRepository;
    private final MonitorObjectService objectService;

    @Override
    public void collectMetric(
            Long vpsId,
            MonitorMetric metric
    ) {

        if (!Boolean.TRUE.equals(metric.getEnabled())) {
            return;
        }

        if (Boolean.TRUE.equals(
                metric.getObjectLevelYn())) {

            collectObjectMetric(
                    vpsId,
                    metric
            );

        } else {

            collectVpsMetric(
                    vpsId,
                    metric
            );
        }
    }

    @Override
    public void collectObjectMetric(
            Long vpsId,
            MonitorMetric metric
    ) {

        List<MonitorObject> objects =
                objectService.findActiveObjects(
                        vpsId,
                        metric.getObjectType()
                );

        for (MonitorObject object : objects) {

            collect(
                    vpsId,
                    metric,
                    object
            );
        }
    }

    @Override
    public void collectVpsMetric(
            Long vpsId,
            MonitorMetric metric
    ) {

        collect(
                vpsId,
                metric,
                null
        );
    }

    @Override
    public void collect(
            Long vpsId,
            MonitorMetric metric,
            MonitorObject object
    ) {

    }
}
