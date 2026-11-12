package com.fraudguard.exception;

public class EventPayloadMismatchException extends RuntimeException {
    public EventPayloadMismatchException(String message) {
        super(message);
    }
}
