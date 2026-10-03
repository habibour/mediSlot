package com.medislot.common.exception;

public class PolicyViolationException extends RuntimeException {

    private final String policy;

    public PolicyViolationException(String policy, String message) {
        super(message);
        this.policy = policy;
    }

    public String getPolicy() {
        return policy;
    }
}
