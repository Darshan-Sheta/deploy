package com.spring.codeamigosbackend.rabbitmq.producer;

import com.spring.codeamigosbackend.recommendation.dtos.GithubScoreRequest;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RabbitMqProducer {

    private Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    private final RabbitTemplate rabbitTemplate;
    private static final Logger logger = LoggerFactory.getLogger(RabbitMqProducer.class);

    private final String exchangeName = dotenv.get("rabbitmq.exchange");
    private final String routingKey = dotenv.get("rabbitmq.routingKey");

    public void sendUserToQueue(GithubScoreRequest user) {
        logger.info("Sending user to queue", user);
        rabbitTemplate.convertAndSend(exchangeName, routingKey, user);
    }
}
