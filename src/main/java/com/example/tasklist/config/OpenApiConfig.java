package com.example.tasklist.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tasklistOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tasklist API")
                        .version("v1")
                        .description(
                                "Учебный REST-сервис задач с советом через локальный OpenAI-совместимый API (LM Studio)."));
    }
}
