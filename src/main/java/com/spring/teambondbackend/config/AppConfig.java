package com.spring.teambondbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.cloudinary.*;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class AppConfig {

    @Value("${CLOUD_NAME}")
    private String cloudName;

    @Value("${API_KEY}")
    private String apiKey;

    @Value("${API_SECRET}")
    private String apiSecret;

    @Bean
    public Cloudinary getCloudinary() {
        Map config = new HashMap();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        return new Cloudinary(config);
    }
}
