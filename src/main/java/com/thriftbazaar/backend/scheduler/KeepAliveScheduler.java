package com.thriftbazaar.backend.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;

/**
 * Pings the backend's own /health endpoint every 14 minutes
 * to prevent Render free-tier from spinning down the instance.
 *
 * Also keeps the Supabase connection warm so the HikariCP pool
 * doesn't go stale during periods of low traffic.
 */
@Component
public class KeepAliveScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveScheduler.class);

    @Value("${app.backend-url:http://localhost:8081}")
    private String backendUrl;

    // Fires every 14 minutes (Render spins down after 15 min of inactivity)
    @Scheduled(fixedRate = 840_000)
    public void keepAlive() {
        String target = backendUrl + "/health";
        try {
            URL url = new URL(target);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            int status = connection.getResponseCode();
            log.info("[KeepAlive] Pinged {} at {} — HTTP {}", target, LocalDateTime.now(), status);
            connection.disconnect();
        } catch (Exception e) {
            log.warn("[KeepAlive] Ping failed for {} — {}", target, e.getMessage());
        }
    }
}
