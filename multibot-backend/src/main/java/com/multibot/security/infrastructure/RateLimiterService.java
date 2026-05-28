package com.multibot.security.infrastructure;

import com.multibot.security.config.SecurityShieldProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter (Bucket4j token bucket).
 * Single-instance MVP. Przy skali -> Bucket4j z Redis backendem (zmiana tylko tej klasy).
 *
 * Limity dzienne: bucket napelnia sie greedy w ciagu 24h.
 * Bukcety zyja w pamieci (ConcurrentHashMap). Restart = reset (akceptowalne).
 */
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> sessionBuckets = new ConcurrentHashMap<>();

    private final int perIpPerDay;
    private final int perSessionPerDay;

    public RateLimiterService(SecurityShieldProperties props) {
        this.perIpPerDay = props.rateLimit().perIpPerDay();
        this.perSessionPerDay = props.rateLimit().perSessionPerDay();
    }

    /** true = mozna kontynuowac, false = limit przekroczony (429). */
    public boolean tryConsumeForIp(String ip) {
        Bucket bucket = ipBuckets.computeIfAbsent(ip, k -> newDailyBucket(perIpPerDay));
        return bucket.tryConsume(1);
    }

    public boolean tryConsumeForSession(String sessionId) {
        Bucket bucket = sessionBuckets.computeIfAbsent(sessionId, k -> newDailyBucket(perSessionPerDay));
        return bucket.tryConsume(1);
    }

    private static Bucket newDailyBucket(int capacity) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofDays(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
