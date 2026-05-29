import apiClient from './client';

export interface RegisterParams {
  username: string;
  authKey: string;
  saltAuth: string;
  encryptedDek: string;
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
  challenge: string;
  challenge_iv: string;
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
  newSaltEnc: string,
  newKdfParams: { algorithm: string; iterations: number },
) {
  return apiClient.put('/auth/password', {
    oldAuthKey,
    newAuthKeyHash,
    newEncryptedDek,
    newSaltEnc,
    newKdfParams,
  });
}

export function getKdfInfo() {
  return apiClient.get('/auth/kdf-info');
}

export function setRecovery(recoveryData: string, recoverySalt: string, challenge: string, encryptedChallenge: string) {
  return apiClient.put('/auth/recovery', { recoveryData, recoverySalt, challenge, encryptedChallenge });
}

export function getRecovery(username: string) {
  return apiClient.get('/auth/recovery', { params: { username } });
}

export function resetPassword(
  username: string,
  newAuthKeyHash: string,
  newEncryptedDek: string,
  newSaltEnc: string,
  newKdfParams: { algorithm: string; iterations: number },
  encryptedChallenge: string,
) {
  return apiClient.post('/auth/recovery/reset', {
    username,
    newAuthKeyHash,
    newEncryptedDek,
    newSaltEnc,
    newKdfParams,
    encryptedChallenge,
  });
}

export function deleteRecovery(authKey: string) {
  return apiClient.delete('/auth/recovery', { data: { authKey }, skipAuthExpiredEvent: true } as never);
}

export function deleteAccount(authKey: string) {
  return apiClient.delete('/auth/account', { data: { authKey }, skipAuthExpiredEvent: true } as never);
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
