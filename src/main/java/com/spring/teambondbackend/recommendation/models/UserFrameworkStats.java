package com.spring.teambondbackend.recommendation.models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString
@Document(collection = "user_framework_stats")
public class UserFrameworkStats {
    @Id
    private String id;
    @Indexed
    private String userId;
    Map<String,Integer> frameworkUsage;
    private LocalDateTime lastUpdated;
}
