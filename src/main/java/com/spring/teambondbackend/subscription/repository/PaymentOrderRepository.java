package com.spring.teambondbackend.subscription.repository;

import com.spring.teambondbackend.subscription.model.PaymentOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentOrderRepository extends MongoRepository<PaymentOrder, String> {
    public PaymentOrder findByOrderId(String orderId);
}
