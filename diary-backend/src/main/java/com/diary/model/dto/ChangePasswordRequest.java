package com.diary.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class ChangePasswordRequest {

    @NotBlank
    private String oldAuthKey;

    @NotBlank
    private String newAuthKeyHash;

    @NotBlank
    private String newEncryptedDek;

    @NotBlank
    private String newEncryptedDekRecovery;

    @NotBlank
    private String newSaltEnc;

    @NotNull
    private Map<String, Object> newKdfParams;

    public String getOldAuthKey() { return oldAuthKey; }
    public void setOldAuthKey(String oldAuthKey) { this.oldAuthKey = oldAuthKey; }

    public String getNewAuthKeyHash() { return newAuthKeyHash; }
    public void setNewAuthKeyHash(String newAuthKeyHash) { this.newAuthKeyHash = newAuthKeyHash; }

    public String getNewEncryptedDek() { return newEncryptedDek; }
    public void setNewEncryptedDek(String newEncryptedDek) { this.newEncryptedDek = newEncryptedDek; }

    public String getNewEncryptedDekRecovery() { return newEncryptedDekRecovery; }
    public void setNewEncryptedDekRecovery(String newEncryptedDekRecovery) { this.newEncryptedDekRecovery = newEncryptedDekRecovery; }

    public String getNewSaltEnc() { return newSaltEnc; }
    public void setNewSaltEnc(String newSaltEnc) { this.newSaltEnc = newSaltEnc; }

    public Map<String, Object> getNewKdfParams() { return newKdfParams; }
    public void setNewKdfParams(Map<String, Object> newKdfParams) { this.newKdfParams = newKdfParams; }
}
