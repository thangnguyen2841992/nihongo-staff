package com.nihongo.staff;

import com.nihongo.security.CommonSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Import(CommonSecurityConfig.class)
public class StaffApplication {

    public static void main(String[] args) {
        SpringApplication.run(StaffApplication.class, args);
    }

}
