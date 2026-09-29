package com.nihongo.staff.service.monitor.collection;

/** Published inside the write transaction; sockets receive it only after commit. */
public record PerformanceChanged(long vpsId, String metricCode) {}
