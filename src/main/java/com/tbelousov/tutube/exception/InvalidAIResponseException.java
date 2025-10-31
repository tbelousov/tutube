package com.tbelousov.tutube.exception;

public class InvalidAIResponseException extends RuntimeException {
    public InvalidAIResponseException(String message) {
        super(message);
    }
}