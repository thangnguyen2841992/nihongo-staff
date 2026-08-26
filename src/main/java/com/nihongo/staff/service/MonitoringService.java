package com.nihongo.staff.service;

import com.nihongo.staff.model.dto.MonitoringResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final RestTemplate restTemplate;

    private volatile MonitoringResponse cachedData;
    private volatile long cachedAt = 0;

    private static final long CACHE_TIME = 5_000; // 5 giây


    public synchronized MonitoringResponse getMetrics() {

        long now = System.currentTimeMillis();

        // Cache còn hạn
        if (cachedData != null
                && now - cachedAt < CACHE_TIME) {

            return cachedData;
        }

        // Cache hết hạn
        MonitoringResponse data =
                fetchFromPrometheus();

        // Cập nhật cache
        cachedData = data;
        cachedAt = now;

        return data;
    }


    private MonitoringResponse fetchFromPrometheus() {

        return new MonitoringResponse(
                20.5,
                42.3,
                65.2,
                864000
        );
    }
}