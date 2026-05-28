package com.diary.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)", length = 36, nullable = false)
    private String id;

    @Column(name = "username", length = 32, nullable = false, unique = true)
    private String username;

    @Column(name = "auth_key_hash", length = 255, nullable = false)
    private String authKeyHash;

    @Column(name = "salt_auth", length = 64, nullable = false)
    private String saltAuth;

    @Column(name = "encrypted_dek", columnDefinition = "TEXT", nullable = false)
    private String encryptedDek;

    @Column(name = "encrypted_dek_recovery", columnDefinition = "TEXT", nullable = false)
    private String encryptedDekRecovery;

    @Column(name = "salt_enc", length = 64, nullable = false)
    private String saltEnc;

    @Column(name = "kdf_version", nullable = false)
    private int kdfVersion;

    @Column(name = "kdf_params", columnDefinition = "JSON", nullable = false)
    private String kdfParams;

    @Column(name = "recovery_data", columnDefinition = "TEXT")
    private String recoveryData;

    @Column(name = "recovery_salt", length = 64)
    private String recoverySalt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public User() {}

    public User(String id, String username, String authKeyHash, String saltAuth,
                String encryptedDek, String encryptedDekRecovery, String saltEnc,
                int kdfVersion, String kdfParams) {
        this.id = id;
        this.username = username;
        this.authKeyHash = authKeyHash;
        this.saltAuth = saltAuth;
        this.encryptedDek = encryptedDek;
        this.encryptedDekRecovery = encryptedDekRecovery;
        this.saltEnc = saltEnc;
        this.kdfVersion = kdfVersion;
        this.kdfParams = kdfParams;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAuthKeyHash() { return authKeyHash; }
    public void setAuthKeyHash(String authKeyHash) { this.authKeyHash = authKeyHash; }

    public String getSaltAuth() { return saltAuth; }
    public void setSaltAuth(String saltAuth) { this.saltAuth = saltAuth; }

    public String getEncryptedDek() { return encryptedDek; }
    public void setEncryptedDek(String encryptedDek) { this.encryptedDek = encryptedDek; }

    public String getEncryptedDekRecovery() { return encryptedDekRecovery; }
    public void setEncryptedDekRecovery(String encryptedDekRecovery) { this.encryptedDekRecovery = encryptedDekRecovery; }

    public String getSaltEnc() { return saltEnc; }
    public void setSaltEnc(String saltEnc) { this.saltEnc = saltEnc; }

    public int getKdfVersion() { return kdfVersion; }
    public void setKdfVersion(int kdfVersion) { this.kdfVersion = kdfVersion; }

    public String getKdfParams() { return kdfParams; }
    public void setKdfParams(String kdfParams) { this.kdfParams = kdfParams; }

    public String getRecoveryData() { return recoveryData; }
    public void setRecoveryData(String recoveryData) { this.recoveryData = recoveryData; }

    public String getRecoverySalt() { return recoverySalt; }
    public void setRecoverySalt(String recoverySalt) { this.recoverySalt = recoverySalt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
