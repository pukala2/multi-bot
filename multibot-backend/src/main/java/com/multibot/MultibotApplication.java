package com.multibot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic(systemName = "Multibot")
public class MultibotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultibotApplication.class, args);
    }
}
