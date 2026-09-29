package com.nihongo.staff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import com.nihongo.staff.service.monitor.vps.PrometheusTargetService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:staff_startup;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "monitoring.collection.enabled=false", "MONITORING_PROMETHEUS_SSH_PASSWORD=",
        "jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA="
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class StaffApplicationTests {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    String bearer(String role) throws Exception {
        var jwt = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
                new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("test").claim("roles", java.util.List.of(role))
                        .expirationTime(java.util.Date.from(java.time.Instant.now().plusSeconds(300))).build());
        jwt.sign(new com.nimbusds.jose.crypto.MACSigner(java.util.Base64.getDecoder().decode("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA=")));
        return "Bearer " + jwt.serialize();
    }
    @Test void eventApiRejectsUserRoleAndUnauthenticatedRequests() throws Exception {
        String path = "/api/staff/vps/1/metrics/MEMORY_USAGE/event-rules";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .header("Authorization", bearer("USER")).contentType("application/json").content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        for (String suffix : java.util.List.of("event-rules", "events")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/staff/vps/1/metrics/MEMORY_USAGE/" + suffix).header("Authorization", bearer("USER")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        }
    }
    @Test void eventApiPersistsRulesAndReturnsValidationMessages() throws Exception {
        long vpsId = eventRule(assignment());
        String path = "/api/staff/vps/" + vpsId + "/metrics/MEMORY_USAGE/event-rules";
        String body = "{\"name\":\"Memory fatal\",\"objectKey\":\"all\",\"operator\":\"GTE\",\"threshold\":95,\"severity\":\"FATAL\",\"consecutiveSamples\":2,\"enabled\":true}";
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .header("Authorization", bearer("STAFF")).contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.severity").value("FATAL")).andReturn();
        long ruleId = mapper.readTree(response.getResponse().getContentAsString()).path("ruleId").asLong();
        assertTrue(ruleId > 0);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path + "/" + ruleId)
                .header("Authorization", bearer("STAFF")).contentType("application/json").content(body.replace("FATAL", "MINOR")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.severity").value("MINOR"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path + "/" + ruleId)
                .header("Authorization", bearer("ADMIN")).contentType("application/json").content(body.replace("\"consecutiveSamples\":2", "\"consecutiveSamples\":0")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").isNotEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path + "/" + ruleId)
                .header("Authorization", bearer("ADMIN")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        assertEquals(1, eventRules.rules(vpsId, "MEMORY_USAGE").size());
    }
    @Autowired com.nihongo.staff.service.IStaffService content;
    @Autowired org.springframework.cache.CacheManager caches;

    com.nihongo.staff.model.dto.BookResponse book(String name) {
        return content.createNewBook(new com.nihongo.staff.model.dto.CreateNewBookRequest(
                content.getTypes().get(0).getTypeId(), content.getLevels().get(0).getLevelId(),
                name, "", java.util.List.of("https://example.test/cover.png")));
    }

    @Test void editingABookInvalidatesThePreviouslyCachedList() {
        var book = book("Before edit");
        caches.getCache("books").clear();
        assertTrue(content.getBooks().stream().anyMatch(b -> b.getBookId().equals(book.getBookId()) && b.getBookName().equals("Before edit")));
        content.updateBook(new com.nihongo.staff.model.dto.UpdateBookRequest(book.getBookId(), "After edit", "",
                content.getLevels().get(0).getLevelId(), content.getTypes().get(0).getTypeId()));
        assertEquals("After edit", content.getBooks().stream().filter(b -> b.getBookId().equals(book.getBookId())).findFirst().orElseThrow().getBookName());
    }

    @Test void anImageCannotBeDeletedThroughAnotherBook() {
        var first = book("First book");
        var second = book("Second book");
        long imageId = second.getImageUrls().get(0).getImageId();
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> content.updateImagesOfBooks(
                new com.nihongo.staff.model.dto.UpdateImageOfBookRequest(first.getBookId(), java.util.List.of(imageId), java.util.List.of())));
        assertTrue(content.getBookDetail(second.getBookId()).getImageUrls().stream().anyMatch(image -> image.getImageId().equals(imageId)));
    }

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.messaging.simp.SimpMessagingTemplate messaging;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.nihongo.staff.repository.MonitorVpsRepository servers;
    @Autowired com.nihongo.staff.repository.MonitorVpsMetricRepository assignments;
    @Autowired com.nihongo.staff.service.monitor.collection.MetricCatalog catalog;
    @Autowired com.nihongo.staff.service.monitor.collection.PerfCollectionStore store;
    @Autowired com.nihongo.staff.service.monitor.event.MonitorEventService eventRules;
    long eventRule(long id) {
        long vpsId = new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> assignments.findById(id).orElseThrow().getVps().getVpsId());
        eventRules.save(vpsId, "MEMORY_USAGE", null, new com.nihongo.staff.service.monitor.event.MonitorEventService.Input(
                "Memory high", "vps", com.nihongo.staff.model.monitoring.MonitorEventRule.Operator.GTE,
                20D, com.nihongo.staff.model.monitoring.MonitorEventRule.Severity.WARNING, 1, true));
        return vpsId;
    }

    long assignment() {
        return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
            var vps = new com.nihongo.staff.model.monitoring.MonitorVps();
            vps.setHostname(java.util.UUID.randomUUID().toString()); vps.setIpAddress("127.0.0.1"); vps.setAgentPort(9100);
            servers.save(vps); catalog.bindDefaults(vps);
            return assignments.findByVps_VpsIdOrderByMetric_MetricNameAsc(vps.getVpsId()).stream()
                    .filter(a -> a.getMetric().getMetricCode().equals("MEMORY_USAGE")).findFirst().orElseThrow().getVpsMetricId();
        });
    }
    @Test void socketReceivesPersistedSnapshotOnlyAfterCommit() {
        long id = assignment();
        eventRule(id);
        org.mockito.Mockito.clearInvocations(messaging);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            var job = store.claim(id);
            store.complete(job, java.util.List.of(new com.nihongo.staff.service.monitor.collection.NodeMetricSource.Reading("VPS", "vps", java.util.Map.of(), 25D, null, null)), java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            org.mockito.Mockito.verifyNoInteractions(messaging);
        });
        var frame = org.mockito.ArgumentCaptor.forClass(com.nihongo.staff.service.monitor.realtime.PerformanceSocketPublisher.Update.class);
        org.mockito.Mockito.verify(messaging).convertAndSend(org.mockito.ArgumentMatchers.startsWith("/topic/vps-performance/"), frame.capture());
        assertEquals(25D, frame.getValue().performance().objects().get(0).points().get(0).value());
        assertEquals("UP", frame.getValue().performance().state());
        assertEquals(1, frame.getValue().performance().events().size());
        assertEquals(25D, frame.getValue().performance().events().get(0).value());
    }
    @Test void rolledBackCollectionDoesNotSendSocketData() {
        long id = assignment();
        long vpsId = eventRule(id);
        org.mockito.Mockito.clearInvocations(messaging);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            store.complete(store.claim(id), java.util.List.of(new com.nihongo.staff.service.monitor.collection.NodeMetricSource.Reading("VPS", "vps", java.util.Map.of(), 40D, null, null)), java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            status.setRollbackOnly();
        });
        org.mockito.Mockito.verifyNoInteractions(messaging);
        assertTrue(eventRules.events(vpsId, "MEMORY_USAGE", null).isEmpty());
    }

    @Autowired PrometheusTargetService vpsService;

	@Test
	void contextLoads() {
	}

    @Test
    void missingSshPasswordFailsOnlyWhenSyncIsRequested() {
        RuntimeException error = assertThrows(RuntimeException.class, () -> vpsService.sync());
        assertNotNull(error.getCause());
        assertTrue(error.getCause().getMessage().contains("MONITORING_PROMETHEUS_SSH_PASSWORD"));
    }

}
