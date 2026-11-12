package com.fraudguard.exception;

public class AmountSpikeSnapshotExpiredException extends RuntimeException {

    public AmountSpikeSnapshotExpiredException(String message) {
        super(message);
    }
}