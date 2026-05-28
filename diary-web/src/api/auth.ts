import apiClient from './client';

export interface RegisterParams {
  username: string;
  authKey: string;
  saltAuth: string;
  encryptedDek: string;
  encryptedDekRecovery: string;
  saltEnc: string;
  kdfVersion: number;
  kdfParams: {
    algorithm: string;
    iterations: number;
  };
}

export interface LoginResponse {
  userId: string;
  encryptedDek: string;
  encryptedDekRecovery: string;
  saltEnc: string;
  kdfVersion: number;
  kdfParams: {
    algorithm: string;
    iterations: number;
  };
  hasRecovery: boolean;
}

export interface RecoveryResponse {
  recovery_data: string;
  recovery_salt: string;
  salt_enc: string;
  encrypted_dek_recovery: string;
  recovery_token: string;
}

export function register(params: RegisterParams) {
  return apiClient.post('/auth/register', params);
}

export function login(username: string, authKey: string) {
  return apiClient.post('/auth/login', { username, authKey });
}

export function logout() {
  return apiClient.post('/auth/logout');
}

export function changePassword(
  oldAuthKey: string,
  newAuthKeyHash: string,
  newEncryptedDek: string,
  newEncryptedDekRecovery: string,
  newSaltEnc: string,
  newKdfParams: { algorithm: string; iterations: number },
) {
  return apiClient.put('/auth/password', {
    oldAuthKey,
    newAuthKeyHash,
    newEncryptedDek,
    newEncryptedDekRecovery,
    newSaltEnc,
    newKdfParams,
  });
}

export function getKdfInfo() {
  return apiClient.get('/auth/kdf-info');
}

export function setRecovery(recoveryData: string, recoverySalt: string) {
  return apiClient.put('/auth/recovery', { recoveryData, recoverySalt });
}

export function getRecovery(username: string) {
  return apiClient.get('/auth/recovery', { params: { username } });
}

export function resetPassword(
  username: string,
  recoveryToken: string,
  newAuthKeyHash: string,
  newEncryptedDek: string,
  newEncryptedDekRecovery: string,
  newSaltEnc: string,
  newKdfParams: { algorithm: string; iterations: number },
) {
  return apiClient.post('/auth/recovery/reset', {
    username,
    recoveryToken,
    newAuthKeyHash,
    newEncryptedDek,
    newEncryptedDekRecovery,
    newSaltEnc,
    newKdfParams,
  });
}

export function deleteRecovery(authKey: string) {
  return apiClient.delete('/auth/recovery', { data: { authKey } });
}

export function deleteAccount(authKey: string) {
  return apiClient.delete('/auth/account', { data: { authKey } });
}

export interface ServerConfig {
  kdf: {
    algorithm: string;
    iterations: number;
  };
  limits: {
    max_attachment_size_mb: number;
    max_attachments_per_entry: number;
  };
}

export function getConfig() {
  return apiClient.get<{ code: number; data: ServerConfig }>('/config');
}
