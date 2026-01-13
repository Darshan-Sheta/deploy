package com.spring.teambondbackend.hackathon.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "request")
public class HackathonRequest {
    @Id
    private String id;
    private String hackathonId;
    private String hackathonTitle;
    private String createdBy;
    private String requestedBy;
    private LocalDateTime requestedAt;
    private String status;
    private List<String> frameworks;
}
