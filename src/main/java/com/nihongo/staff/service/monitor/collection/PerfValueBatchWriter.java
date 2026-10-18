package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.MonitorPerfValue;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Uses the caller's JPA transaction; generated value IDs are not needed for alert evaluation. */
@Component
@RequiredArgsConstructor
public class PerfValueBatchWriter {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(List<MonitorPerfValue> samples) {
        if (samples.isEmpty()) return;
        jdbc.batchUpdate("insert into monitor_perf_value (vps_id, metric_id, object_id, collected_at, value) values (?, ?, ?, ?, ?)",
                samples, 500, (statement, sample) -> {
                    statement.setLong(1, sample.getVpsId());
                    statement.setLong(2, sample.getMetricId());
                    if (sample.getObjectId() == null) statement.setNull(3, Types.BIGINT);
                    else statement.setLong(3, sample.getObjectId());
                    statement.setTimestamp(4, Timestamp.valueOf(sample.getCollectedAt()));
                    statement.setDouble(5, sample.getValue());
                });
    }
}
