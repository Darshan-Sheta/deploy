package com.spring.teambondbackend.personalchat.controller;

import com.spring.teambondbackend.personalchat.model.Message;
import com.spring.teambondbackend.personalchat.model.PersonalChat;

import com.spring.teambondbackend.personalchat.payload.MessageReqestPersonalChat;
import com.spring.teambondbackend.personalchat.repository.PersonalChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
public class PersonalChatChatController {
    private final PersonalChatRepository personalChatRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MongoTemplate mongoTemplate;

    @MessageMapping("/personal_chat/send_message/{member1Id}/{member2Id}")
    public void sendMessage(@DestinationVariable String member1Id, @DestinationVariable String member2Id,
            @Payload MessageReqestPersonalChat messageRequest) {

        System.out.println("Processing message for members: " + member1Id + " and " + member2Id);

        // Reconstruct chatIds for topic broadcasting if needed, or just use the IDs
        String chatIds = member1Id + "/" + member2Id;
        String[] ids = { member1Id, member2Id }; // We already have them split

        if (member1Id == null || member2Id == null || member1Id.isEmpty() || member2Id.isEmpty()) {
            throw new RuntimeException("Invalid member IDs");
        }

        Optional<PersonalChat> personalChat = personalChatRepository.findByMemberIds(member1Id, member2Id);
        if (personalChat.isEmpty()) {
            System.err.println("Personal chat not found for members: " + member1Id + " and " + member2Id);
            throw new RuntimeException("Personal chat not found");
        }

        Message message = new Message();
        message.setContent(messageRequest.getContent());
        message.setTimestamp(LocalDateTime.now());
        message.setSender(messageRequest.getSender());

        System.out.println("Created message object: " + message);

        try {
            // Write debug log to file
            try (java.io.FileWriter fw = new java.io.FileWriter("chat_debug.log", true);
                    java.io.BufferedWriter bw = new java.io.BufferedWriter(fw);
                    java.io.PrintWriter out = new java.io.PrintWriter(bw)) {
                out.println("Processing message for chat IDs: " + chatIds);
                out.println("Found chat with DB ID: " + personalChat.get().getId());
            } catch (Exception ex) {
                // ignore
            }

            // Use MongoTemplate to atomically push the message to the list
            // Use "id" (no underscore) to let Spring Data handle the mapping to _id
            // (ObjectId vs String)
            Query query = new Query(Criteria.where("id").is(personalChat.get().getId()));
            Update update = new Update().push("messages", message);

            com.mongodb.client.result.UpdateResult result = mongoTemplate.updateFirst(query, update,
                    PersonalChat.class);

            System.out.println("MongoTemplate Update Result:");
            System.out.println("Matched Count: " + result.getMatchedCount());
            System.out.println("Modified Count: " + result.getModifiedCount());

            // If atomic update fails (e.g. document not found by query), fallback to
            // repository save
            if (result.getMatchedCount() == 0 || result.getModifiedCount() == 0) {
                System.err.println("WARNING: Atomic update failed. Falling back to repository save.");

                PersonalChat chatToSave = personalChatRepository.findById(personalChat.get().getId())
                        .orElseThrow(() -> new RuntimeException("Chat disappeared during processing"));

                if (chatToSave.getMessages() == null) {
                    chatToSave.setMessages(new java.util.ArrayList<>());
                }
                chatToSave.getMessages().add(message);
                personalChatRepository.save(chatToSave);
                System.out.println("Fallback repository save completed.");
            }

        } catch (Exception e) {
            System.err.println("ERROR saving chat to MongoDB: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to save message to database", e);
        }

        System.out.println("Message saved successfully. Broadcasting to topic: /api/v1/topic/personal_chat/" + chatIds);

        // Send the message to the topic using the same chatIds format
        messagingTemplate.convertAndSend("/api/v1/topic/personal_chat/" + chatIds, message);
    }
}
