package com.example.tasklist.lmstudio;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(LmStudioProperties.class)
public class LmStudioConfig {

    public static final String LM_STUDIO_REST_CLIENT = "lmStudioRestClient";

    @Bean
    @Qualifier(LM_STUDIO_REST_CLIENT)
    public RestClient lmStudioRestClient(LmStudioProperties props) {
        int connectMs = Math.max(1, props.getConnectTimeoutSeconds()) * 1000;
        int readMs = Math.max(1, props.getTimeoutSeconds()) * 1000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);

        String base = props.getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return RestClient.builder()
                .baseUrl(base)
                .requestFactory(factory)
                .build();
    }
}
