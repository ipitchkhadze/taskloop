package com.example.tasklist.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI taskLoopOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TaskLoop API")
                        .version("v1")
                        .description(
                                "TaskLoop: REST-сервис досок и задач с советом через локальный OpenAI-совместимый API (LM Studio)."));
    }
}
