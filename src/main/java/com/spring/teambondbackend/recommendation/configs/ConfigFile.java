package com.spring.teambondbackend.recommendation.configs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
@Configuration
public class ConfigFile {
        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }
}
