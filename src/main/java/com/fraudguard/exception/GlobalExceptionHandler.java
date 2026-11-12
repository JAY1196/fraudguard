package com.fraudguard.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("urn:problem:validation-error"));
        problemDetail.setProperty("code", "VALIDATION_ERROR");
        problemDetail.setProperty("timestamp", Instant.now());

        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    @ExceptionHandler(TimestampOutOfRangeException.class)
    public ProblemDetail handleTimestampOutOfRangeException(TimestampOutOfRangeException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problemDetail.setTitle("Timestamp Out Of Range");
        problemDetail.setType(URI.create("urn:problem:timestamp-out-of-range"));
        problemDetail.setProperty("code", "TIMESTAMP_OUT_OF_RANGE");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(AmountSpikeSnapshotExpiredException.class)
    public ProblemDetail handleAmountSpikeSnapshotExpiredException(
            AmountSpikeSnapshotExpiredException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problemDetail.setTitle("Amount Spike Snapshot Expired");
        problemDetail.setType(URI.create("urn:problem:amount-spike-snapshot-expired"));
        problemDetail.setProperty("code", "AMOUNT_SPIKE_SNAPSHOT_EXPIRED");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
    @ExceptionHandler(EventPayloadMismatchException.class)
    public ProblemDetail handleEventPayloadMismatchException(EventPayloadMismatchException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage()
        );
        problemDetail.setTitle("Event Payload Mismatch");
        problemDetail.setType(URI.create("urn:problem:event-payload-mismatch"));
        problemDetail.setProperty("code", "EVENT_PAYLOAD_MISMATCH");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                resolveMessageNotReadableDetail(ex)
        );
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("urn:problem:validation-error"));
        problemDetail.setProperty("code", "VALIDATION_ERROR");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred"
        );
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("urn:problem:internal-error"));
        problemDetail.setProperty("code", "INTERNAL_SERVER_ERROR");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    private String resolveMessageNotReadableDetail(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause != null && cause.getMessage() != null ? cause.getMessage() : "";
        if (message.contains("UUID")) {
            return "Invalid UUID format: " + message;
        }
        if (message.contains("TransactionType") || message.contains("Enum")) {
            return "Invalid value for field 'type': not one of the allowed values [TOPUP, TRANSFER, WITHDRAWAL, REVERSAL]";
        }
        return "Malformed JSON request body";
    }
}
