package com.spring.teambondbackend.personalchat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String sender;
    private String content;
    private LocalDateTime timestamp;
    
    // Convenience constructor
    public Message(String content, String sender) {
        this.content = content;
        this.sender = sender;
        this.timestamp = LocalDateTime.now();
    }
}
