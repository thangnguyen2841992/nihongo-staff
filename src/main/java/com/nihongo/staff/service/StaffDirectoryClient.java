package com.nihongo.staff.service;
import org.springframework.cloud.openfeign.FeignClient;import org.springframework.web.bind.annotation.GetMapping;import java.util.List;
@FeignClient(name="gateway-service",contextId="staffDirectory",url="${services.gateway-url:http://localhost:8082}")
public interface StaffDirectoryClient {
 record Member(String userId,String name,String role) {}
 @GetMapping("/api/users/staff-directory") List<Member> list();
}
