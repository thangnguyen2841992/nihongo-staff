package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.ExporterType;
import com.nihongo.staff.model.monitoring.MonitorMetric;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.MonitorVpsMetric;
import com.nihongo.staff.repository.MonitorMetricRepository;
import com.nihongo.staff.repository.MonitorVpsMetricRepository;
import com.nihongo.staff.repository.MonitorVpsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MetricCatalogMysqlTest {
    @Test void assignsOnlyMysqlMetricsToMysqlTarget() {
        var metrics = mock(MonitorMetricRepository.class);
        var links = mock(MonitorVpsMetricRepository.class);
        var catalog = new MetricCatalog(metrics, links, mock(MonitorVpsRepository.class));
        when(metrics.findAll()).thenReturn(List.of(metric(1, "CPU_USAGE", "NODE_EXPORTER"),
                metric(2, "MYSQL_THREADS_CONNECTED", "MYSQL_JDBC")));
        var target = new MonitorVps();
        target.setVpsId(7L);
        target.setExporterType(ExporterType.MYSQL_JDBC);

        catalog.bindDefaults(target);

        var saved = ArgumentCaptor.forClass(MonitorVpsMetric.class);
        verify(links).save(saved.capture());
        assertEquals("MYSQL_THREADS_CONNECTED", saved.getValue().getMetric().getMetricCode());
    }

    private static MonitorMetric metric(long id, String code, String collector) {
        var metric = new MonitorMetric();
        metric.setMetricId(id);
        metric.setMetricCode(code);
        metric.setCollectorType(collector);
        metric.setDefaultMetric(true);
        return metric;
    }
}
