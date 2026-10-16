package com.nihongo.staff;
import com.nihongo.security.CommonSecurityConfig;
import com.nihongo.staff.config.SecurityConfig;
import com.nihongo.staff.controller.ContentOperationsController;
import com.nihongo.staff.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
@WebMvcTest(value=ContentOperationsController.class,properties={"spring.config.import=","security.session-validation.enabled=false","security.origin-check.enabled=false"}) @Import({SecurityConfig.class,CommonSecurityConfig.class})
class ContentOperationsAuthorizationTest {
 @Autowired MockMvc mvc;@MockitoBean JwtDecoder decoder;@MockitoBean ContentOperationsService operations;@MockitoBean ContentRevisionService revisions;
 @Test void userCannotReadBankReportsOfOthersOrVersions() throws Exception {
  for(String path:new String[]{"questions","issues","exams","revisions/BOOK/1","reviews"})mvc.perform(get("/api/staff/content/"+path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());verifyNoInteractions(operations,revisions);
 }
 @Test void staffCannotPublishOrReadAdminAudit() throws Exception {
  mvc.perform(post("/api/staff/content/reviews/1").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))).contentType("application/json").content("{\"action\":\"PUBLISH\",\"expectedStatus\":\"IN_REVIEW\"}")).andExpect(status().isForbidden());
  mvc.perform(get("/api/staff/content/audit/operations").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))).andExpect(status().isForbidden());verifyNoInteractions(operations);
 }
 @Test void reporterAndMineAlwaysUseJwtSubject() throws Exception {
  mvc.perform(post("/api/staff/content/reports").with(jwt().jwt(j->j.subject("learner")).authorities(new SimpleGrantedAuthority("ROLE_USER"))).contentType("application/json").content("{\"lessonId\":1,\"category\":\"AUDIO\",\"detail\":\"Không nghe được file âm thanh\",\"reporter\":\"someone-else\"}")).andExpect(status().isOk());verify(operations).report(eq("learner"),any(),any());
 }
 @Test void emptyExamAndShortReportAreInvalid() throws Exception {
  mvc.perform(post("/api/staff/content/exams").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))).contentType("application/json").content("{\"title\":\"Đề\",\"questionIds\":[]}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/staff/content/reports").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))).contentType("application/json").content("{\"lessonId\":1,\"category\":\"AUDIO\",\"detail\":\"ngắn\"}")).andExpect(status().isBadRequest());verifyNoInteractions(operations);
 }
}
