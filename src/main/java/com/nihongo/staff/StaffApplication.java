package com.nihongo.staff;

import com.nihongo.security.CommonSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Import(CommonSecurityConfig.class)
// Cache advice runs outside the transaction advice (both default to the lowest precedence, which leaves their order
// to registration order), so a cache hit never borrows a database connection. Method security stays outermost.
@EnableCaching(order = org.springframework.core.Ordered.LOWEST_PRECEDENCE - 1)
public class StaffApplication {

    public static void main(String[] args) {
        SpringApplication.run(StaffApplication.class, args);
    }

}
