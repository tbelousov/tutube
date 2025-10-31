package com.tbelousov.tutube.exception;

public class UserActionNotFoundException extends RuntimeException {
    public UserActionNotFoundException(String message) {
        super(message);
    }
}