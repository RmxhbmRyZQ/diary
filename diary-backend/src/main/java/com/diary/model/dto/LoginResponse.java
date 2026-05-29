package com.diary.model.dto;

import java.util.Map;

public class LoginResponse {

    private String userId;
    private String encryptedDek;
    private String saltEnc;
    private int kdfVersion;
    private Map<String, Object> kdfParams;
    private boolean hasRecovery;

    public LoginResponse(String userId, String encryptedDek,
                         String saltEnc, int kdfVersion, Map<String, Object> kdfParams,
                         boolean hasRecovery) {
        this.userId = userId;
        this.encryptedDek = encryptedDek;
        this.saltEnc = saltEnc;
        this.kdfVersion = kdfVersion;
        this.kdfParams = kdfParams;
        this.hasRecovery = hasRecovery;
    }

    public String getUserId() { return userId; }
    public String getEncryptedDek() { return encryptedDek; }
    public String getSaltEnc() { return saltEnc; }
    public int getKdfVersion() { return kdfVersion; }
    public Map<String, Object> getKdfParams() { return kdfParams; }
    public boolean isHasRecovery() { return hasRecovery; }
}
