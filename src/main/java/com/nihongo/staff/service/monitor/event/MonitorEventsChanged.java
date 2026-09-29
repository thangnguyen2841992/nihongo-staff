package com.nihongo.staff.service.monitor.event;
import java.util.List;
public record MonitorEventsChanged(long vpsId, List<Long> eventIds) {}
