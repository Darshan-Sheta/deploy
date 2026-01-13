package com.spring.teambondbackend.recommendation.repositories;

import com.spring.teambondbackend.recommendation.models.UserFrameworkStats;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserFrameworkStatsRepository extends MongoRepository<UserFrameworkStats,String> {


    Optional<UserFrameworkStats> findByUserId(String userId);
}
