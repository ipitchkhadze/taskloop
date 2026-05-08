package com.example.tasklist;

import com.example.tasklist.web.AdviceRateLimitProperties;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AdviceRateLimitProperties.class)
public class TasklistApplication {

    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(TasklistApplication.class, args);
    }

    /**
     * Подхватывает переменные из файла {@code .env} в корне проекта (рабочий каталог при запуске).
     * Не перезаписывает уже заданные {@linkplain System#getenv() переменные окружения} ОС.
     */
    private static void loadDotenv() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        for (DotenvEntry e : dotenv.entries()) {
            String key = e.getKey();
            if (System.getenv(key) != null) {
                continue;
            }
            String value = e.getValue();
            if (value != null) {
                System.setProperty(key, value);
            }
        }
    }
}
