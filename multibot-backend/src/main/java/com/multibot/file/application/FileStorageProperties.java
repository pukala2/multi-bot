package com.multibot.file.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "multibot.file.storage")
public record FileStorageProperties(String mode, Local local) {

    public record Local(String basePath) {
    }
}
