package com.nihongo.staff.service;
import org.springframework.cloud.openfeign.FeignClient;import org.springframework.web.bind.annotation.GetMapping;import java.util.List;
// Direct service-to-service call (via discovery), without an extra gateway hop.
@FeignClient(name="user-service",contextId="staffDirectory",url="${services.user.url:}")
public interface StaffDirectoryClient {
 record Member(String userId,String name,String role) {}
 @GetMapping("/api/users/staff-directory") List<Member> list();
}
