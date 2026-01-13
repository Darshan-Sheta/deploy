package com.spring.teambondbackend.rabbitmq.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    @Value("${rabbitmq.queue}")
    private String queue;

    @Value("${rabbitmq.dlx.exchange}")
    private String dlxExchange;

    @Value("${rabbitmq.dlq.routingKey}")
    private String dlqRoutingKey;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routingKey}")
    private String routingKey;

    @Value("${rabbitmq.dlq.queue}")
    private String dlqQueue;

    @Bean
    public Queue rabbitMqQueue() {
        // Create the main queue with dead letter configuration
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", dlxExchange)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(exchange);
    }

    // Now bind the queue with the exchange using the routing key
    @Bean
    public Binding rabbitMqBinding() {
        return BindingBuilder
                .bind(rabbitMqQueue())
                .to(topicExchange())
                .with(routingKey);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        // Create the dead letter exchange
        return new TopicExchange(dlxExchange);
    }

    @Bean
    public Queue deadLetterQueue() {
        // Create the dead letter queue
        return QueueBuilder.durable(dlqQueue).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        // Bind the dead letter queue to the dead letter exchange
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(dlqRoutingKey);
    }

    // We will also use the RabbitTemplate , ConnectionFactory and RabbitAdmin beans
    // as well
    // Springboot automatically configures them (Autoconfiguration)
    // Thus no need to create it

    // If we want to deal with json then add convertor in the RabbitTemplate

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());
        return rabbitTemplate;
    }

    // This is to make sure that when the factory creates the rabbitListener it
    // makes sure that the
    // factory also has the same jackson2JsonConvertor to make sure that the msg is
    // properly deserialized

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            RepublishMessageRecoverer republishMessageRecoverer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        // Added this for automatic error handling with 1s as inital time and then 2 s ,
        // 4s, till 10 as max interval
        // Use the retry mechanism , i .e of the RetryInterceptorBuilder .
        factory.setAdviceChain(RetryInterceptorBuilder.stateless().maxAttempts(5).recoverer(republishMessageRecoverer)
                .backOffOptions(1000, 2.0, 10000).build());
        return factory;
    }

    // Configure message recoverer to send failed messages to DLQ after retries
    @Bean
    public RepublishMessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, dlxExchange, dlqRoutingKey);
    }

}
