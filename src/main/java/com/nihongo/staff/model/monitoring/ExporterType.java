package com.nihongo.staff.model.monitoring;

public enum ExporterType {
    NODE_EXPORTER,
    WINDOWS_EXPORTER;

    public static ExporterType forVps(MonitorVps vps) {
        return vps.getExporterType() == null ? NODE_EXPORTER : vps.getExporterType();
    }
}
