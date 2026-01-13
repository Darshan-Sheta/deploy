package com.spring.teambondbackend.personalchat.controller;

import com.spring.teambondbackend.personalchat.model.PersonalChat;
import com.spring.teambondbackend.personalchat.repository.PersonalChatRepository;
import com.spring.teambondbackend.registration.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class SecretKeyController {
    private final PersonalChatRepository personalChatRepository;
    private final UserService userService;

    @Data
    private static class SecreKeysDto {
        private String secretKey;
        private String secretKey1;
    }

    @PostMapping("/secret_key/{member1Id}/{member2Id}/")
    public ResponseEntity<?> setSecretKey(
            @PathVariable String member1Id,
            @PathVariable String member2Id,
           @RequestBody SecreKeysDto secretKeysDto
    ) {
        // Use findByMemberIds to look up the chat
        Optional<PersonalChat> chat = this.personalChatRepository.findByMemberIds(member1Id, member2Id);
        System.out.println("Looking for chat with member1Id: " + member1Id + ", member2Id: " + member2Id);
        if (chat.isPresent()) {
            chat.get().setSecretKey(member1Id+":"+secretKeysDto.getSecretKey());
            chat.get().setSecretKey1(member2Id+":"+secretKeysDto.getSecretKey1());
            this.personalChatRepository.save(chat.get());
            System.out.println("Updated secret key for chat with _id: " + chat.get().getId());
            return ResponseEntity.ok().build();
        } else {
            System.out.println("Chat not found for member1Id: " + member1Id + ", member2Id: " + member2Id);
            return ResponseEntity.status(404).body("Chat not found");
        }
    }

    @GetMapping("/secret_key/{member1Id}/{member2Id}/")
    public ResponseEntity<?> getSecretKey(
            @PathVariable String member1Id,
            @PathVariable String member2Id) {
        // Use findByMemberIds to look up the chat
        Optional<PersonalChat> chat = this.personalChatRepository.findByMemberIds(member1Id, member2Id);
        System.out.println("Looking for chat with member1Id: " + member1Id + ", member2Id: " + member2Id);
        if (chat.isPresent()) {
            String secretKey = chat.get().getSecretKey();
            String secretKey1 = chat.get().getSecretKey1();
            String currentUserId = this.userService.getCurrentUserId(); // Make a call to get current UserId
            if(secretKey == null && secretKey1 == null) {
                System.out.println("No secret key found");
                return ResponseEntity.status(404).body("Secret key not found");
            }
            if(secretKey.split(":")[0].equals(currentUserId) ){
                System.out.println("Secret key found");
                return ResponseEntity.ok(secretKey.split(":")[1]);
            }
            if(secretKey1.split(":")[0].equals(currentUserId) ){
                System.out.println("Secret key 1 found");
                return ResponseEntity.ok(secretKey1.split(":")[1]);
            }
            else{
                System.out.println("Secret key not found");
                return ResponseEntity.status(404).body("Secret key not found");
            }
        } else {
            System.out.println("Chat not found for member1Id: " + member1Id + ", member2Id: " + member2Id);
            return ResponseEntity.status(404).body("Chat not found");
        }
    }
}
