package com.spring.teambondbackend.recommendation.utils;

import lombok.Getter;

@Getter
public class ApiResponse {
    int statusCode;
    Object data;
    String message;
    public ApiResponse(int statusCode, Object data, String message) {
        this.statusCode = statusCode;
        this.data = data;
        this.message = message;
    }
}
