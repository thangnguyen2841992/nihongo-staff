package com.nihongo.staff.security;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="nihongo-user-service",contextId="subscriptionAccess",url="${services.nihongo-user-url:}")
public interface SubscriptionClient {
 @GetMapping("/api/nihongo-user/access/levels/{levelId}") Boolean hasAccess(@PathVariable("levelId") Long levelId);
}
