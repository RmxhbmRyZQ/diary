package com.diary.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 32)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$")
    private String username;

    @NotBlank
    private String authKey;

    @NotBlank
    private String saltAuth;

    @NotBlank
    private String encryptedDek;

    @NotBlank
    private String saltEnc;

    private int kdfVersion;

    @NotNull
    private Map<String, Object> kdfParams;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAuthKey() { return authKey; }
    public void setAuthKey(String authKey) { this.authKey = authKey; }

    public String getSaltAuth() { return saltAuth; }
    public void setSaltAuth(String saltAuth) { this.saltAuth = saltAuth; }

    public String getEncryptedDek() { return encryptedDek; }
    public void setEncryptedDek(String encryptedDek) { this.encryptedDek = encryptedDek; }

    public String getSaltEnc() { return saltEnc; }
    public void setSaltEnc(String saltEnc) { this.saltEnc = saltEnc; }

    public int getKdfVersion() { return kdfVersion; }
    public void setKdfVersion(int kdfVersion) { this.kdfVersion = kdfVersion; }

    public Map<String, Object> getKdfParams() { return kdfParams; }
    public void setKdfParams(Map<String, Object> kdfParams) { this.kdfParams = kdfParams; }
}
