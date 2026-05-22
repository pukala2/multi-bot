package com.multibot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI multibotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Multibot API")
                        .version("1.0.0")
                        .description("SaaS B2B do zarządzania chatbotami AI z RAG"));
    }
}
