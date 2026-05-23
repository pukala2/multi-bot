package com.multibot.admin.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record AdminStats(
        DocumentStats documents,
        SessionStats sessions,
        MessageStats messages,
        TokenStats tokens,
        Optional<Instant> lastActivity) {

    public record DocumentStats(long total, Map<String, Long> byStatus) {}
    public record SessionStats(long total, long today, long thisWeek) {}
    public record MessageStats(long total, long today) {}
    public record TokenStats(long total, long today, double estimatedCostUsd) {}
}
