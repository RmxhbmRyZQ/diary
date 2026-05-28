package com.diary.model.dto;

import jakarta.validation.constraints.NotBlank;

public class SetRecoveryRequest {

    @NotBlank
    private String recoveryData;

    @NotBlank
    private String recoverySalt;

    public String getRecoveryData() { return recoveryData; }
    public void setRecoveryData(String recoveryData) { this.recoveryData = recoveryData; }

    public String getRecoverySalt() { return recoverySalt; }
    public void setRecoverySalt(String recoverySalt) { this.recoverySalt = recoverySalt; }
}
