package com.rmbreeding.api.shared;

public class MfaRequiredException extends RuntimeException {

    private final String challengeId;

    public MfaRequiredException(String challengeId) {
        super("MFA verification required");
        this.challengeId = challengeId;
    }

    public String getChallengeId() {
        return challengeId;
    }
}
