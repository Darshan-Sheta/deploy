package com.spring.teambondbackend.recommendation.utils;


import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    Integer statusCode;
    String message;
    public ApiException(Integer statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.message = message;
    }

}
