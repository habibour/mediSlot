package com.medislot.common.exception;

public class SlotTakenException extends ConflictException {

    public SlotTakenException(String message) {
        super(message);
    }
}
