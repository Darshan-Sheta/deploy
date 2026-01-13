package com.spring.teambondbackend.rabbitmq.producer;

import com.spring.teambondbackend.recommendation.dtos.GithubScoreRequest;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RabbitMqProducer {

    private final RabbitTemplate rabbitTemplate;
    private static final Logger logger = LoggerFactory.getLogger(RabbitMqProducer.class);

    @org.springframework.beans.factory.annotation.Value("${rabbitmq.exchange}")
    private String exchangeName;

    @org.springframework.beans.factory.annotation.Value("${rabbitmq.routingKey}")
    private String routingKey;

    public void sendUserToQueue(GithubScoreRequest user) {
        logger.info("Sending user to queue", user);
        rabbitTemplate.convertAndSend(exchangeName, routingKey, user);
    }
}
