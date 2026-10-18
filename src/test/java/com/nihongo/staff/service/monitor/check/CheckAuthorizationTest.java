package com.nihongo.staff.service.monitor.check;

import com.nihongo.staff.controller.CheckTargetController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CheckTargetController.class, properties = {"spring.config.import="})
@Import(CheckAuthorizationTest.Config.class)
class CheckAuthorizationTest {
    @TestConfiguration @EnableMethodSecurity
    static class Config {
        @Bean SecurityFilterChain checks(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().authenticated()).build();
        }
    }
    @Autowired MockMvc mvc;
    @MockitoBean CheckTargetService service;
    @Test @WithMockUser(roles = "USER") void learnerCannotListProbeOrRegisterTargets() throws Exception {
        mvc.perform(get("/api/staff/check-targets")).andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/check-targets/page").param("type", "PORT_CHECK")).andExpect(status().isForbidden());
        for (String path : new String[]{"/api/staff/check-targets/probe", "/api/staff/check-targets"})
            mvc.perform(post(path).contentType("application/json").content("{\"name\":\"x\",\"type\":\"PORT_CHECK\",\"host\":\"localhost\",\"port\":80}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles = "STAFF") void staffCanListChecks() throws Exception {
        mvc.perform(get("/api/staff/check-targets")).andExpect(status().isOk()); verify(service).list();
    }
    @Test @WithMockUser(roles = "STAFF") void staffCanRequestAPageAndOverloadReturns503() throws Exception {
        mvc.perform(get("/api/staff/check-targets/page").param("type", "WEB_CHECK").param("page", "1"))
                .andExpect(status().isOk());
        verify(service).page("WEB_CHECK", 1, 20);
        when(service.probe(any())).thenThrow(new CheckMetricSource.ProbeUnavailableException());
        mvc.perform(post("/api/staff/check-targets/probe").contentType("application/json")
                .content("{\"name\":\"x\",\"type\":\"PORT_CHECK\",\"host\":\"localhost\",\"port\":80}"))
                .andExpect(status().isServiceUnavailable());
    }
    @Test @WithMockUser(roles = "ADMIN") void adminCanListChecks() throws Exception {
        mvc.perform(get("/api/staff/check-targets")).andExpect(status().isOk()); verify(service).list();
    }
}
