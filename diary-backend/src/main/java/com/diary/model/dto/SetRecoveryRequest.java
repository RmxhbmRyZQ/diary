package com.diary.model.dto;

import jakarta.validation.constraints.NotBlank;

public class SetRecoveryRequest {

    @NotBlank
    private String recoveryData;

    @NotBlank
    private String recoverySalt;

    @NotBlank
    private String challenge;

    @NotBlank
    private String encryptedChallenge;

    public String getRecoveryData() { return recoveryData; }
    public void setRecoveryData(String recoveryData) { this.recoveryData = recoveryData; }

    public String getRecoverySalt() { return recoverySalt; }
    public void setRecoverySalt(String recoverySalt) { this.recoverySalt = recoverySalt; }

    public String getChallenge() { return challenge; }
    public void setChallenge(String challenge) { this.challenge = challenge; }

    public String getEncryptedChallenge() { return encryptedChallenge; }
    public void setEncryptedChallenge(String encryptedChallenge) { this.encryptedChallenge = encryptedChallenge; }
}
