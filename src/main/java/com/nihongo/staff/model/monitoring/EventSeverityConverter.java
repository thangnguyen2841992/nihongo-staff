package com.nihongo.staff.model.monitoring;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Read legacy INFO rows as MINOR; new writes always use the four current levels. */
@Converter
public class EventSeverityConverter implements AttributeConverter<MonitorEventRule.Severity, String> {
    @Override
    public String convertToDatabaseColumn(MonitorEventRule.Severity severity) {
        return severity == null ? null : severity.name();
    }

    @Override
    public MonitorEventRule.Severity convertToEntityAttribute(String value) {
        if (value == null) return null;
        return "INFO".equals(value) ? MonitorEventRule.Severity.MINOR : MonitorEventRule.Severity.valueOf(value);
    }
}
