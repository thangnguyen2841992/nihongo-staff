package com.nihongo.staff.service.monitor.check;
import com.nihongo.staff.model.monitoring.ExporterType;
import com.nihongo.staff.model.monitoring.dto.CheckTargetRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class CheckTargetServiceTest {
    @Test void normalizesUrlAndRetainsCaseSensitivePathAndQuery() {
        var result = CheckTargetService.validate(new CheckTargetRequest(" Site ", "WEB_CHECK", null, null, "https://EXAMPLE.com:443/Health?q=A", null, null));
        assertEquals("https://example.com/Health?q=A", result.url()); assertEquals(443, result.port()); assertEquals(200, result.statusMin());
        assertEquals("http://[::1]/", CheckTargetService.validate(new CheckTargetRequest("v6", "WEB_CHECK", null, null, "http://[::1]", null, null)).url());
    }
    @Test void rejectsUnsupportedSchemesCredentialsFragmentsAndInvalidStatusRanges() {
        for (String url : new String[]{"file:///etc/passwd", "ftp://example.com", "http://user:pass@example.com", "http://example.com/#hash", "http://example.com:70000"})
            assertThrows(ResponseStatusException.class, () -> CheckTargetService.validate(new CheckTargetRequest("site", "WEB_CHECK", null, null, url, 200, 399)));
        assertThrows(ResponseStatusException.class, () -> CheckTargetService.validate(new CheckTargetRequest("site", "WEB_CHECK", null, null, "https://example.com", 500, 200)));
    }
    @Test void validatesTcpHostAndPortAndRejectsOtherTargetTypes() {
        var result = CheckTargetService.validate(new CheckTargetRequest(" SSH ", "PORT_CHECK", " SERVER.internal ", 22, null, null, null));
        assertEquals(ExporterType.PORT_CHECK, result.type()); assertEquals("server.internal", result.host());
        assertThrows(ResponseStatusException.class, () -> CheckTargetService.validate(new CheckTargetRequest("x", "PORT_CHECK", "http://host/path", 80, null, null, null)));
        assertThrows(ResponseStatusException.class, () -> CheckTargetService.validate(new CheckTargetRequest("x", "PORT_CHECK", "host", 0, null, null, null)));
        assertThrows(ResponseStatusException.class, () -> CheckTargetService.validate(new CheckTargetRequest("x", "KAFKA_ADMIN", "host", 80, null, null, null)));
    }
}
