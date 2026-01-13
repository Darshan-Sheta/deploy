package com.spring.teambondbackend.personalchat.dto;


import com.spring.teambondbackend.personalchat.model.Message;
import lombok.Data;

import java.util.List;

@Data
public class PersonalChatResponseDto {
    private String id;
    private String githubUserName;
    private List<Message> messages;
    public PersonalChatResponseDto(String githubUserName, List<Message> messages,String id) {
        this.githubUserName = githubUserName;
        this.messages = messages;
        this.id = id;
    }
}

