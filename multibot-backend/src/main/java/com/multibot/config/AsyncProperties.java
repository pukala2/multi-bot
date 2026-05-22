package com.multibot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "multibot.async")
public record AsyncProperties(int corePoolSize, int maxPoolSize, int queueCapacity) {
}
