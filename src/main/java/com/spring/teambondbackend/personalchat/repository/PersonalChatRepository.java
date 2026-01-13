package com.spring.teambondbackend.personalchat.repository;


import com.spring.teambondbackend.personalchat.model.PersonalChat;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PersonalChatRepository extends MongoRepository<PersonalChat, String> {
    @Query("{ '$or': [ { 'member1Id': ?0, 'member2Id': ?1 }, { 'member1Id': ?1, 'member2Id': ?0 } ] }")
    Optional<PersonalChat> findByMemberIds(String member1Id, String member2Id);
    List<PersonalChat> findByMember1IdOrMember2Id(String member1Id, String member2Id);
}

