package com.spring.teambondbackend.personalchat.dto;


import com.spring.teambondbackend.personalchat.model.Message;
import lombok.Data;

import java.util.List;

@Data
public class PersonalChatSingleResponseDto {
    private String member1Name;
    private String member2Name;
    private List<Message> message;
}
