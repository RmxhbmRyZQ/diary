import adminClient, { setAdminToken } from './adminClient';

export interface DashboardData {
  total_users: number;
  total_entries: number;
  storage_bytes: number;
}

export interface AdminUser {
  id: string;
  username: string;
  created_at: string;
  entry_count: number;
}

export interface KdfConfig {
  algorithm: string;
  iterations: number;
}

export interface AttachmentConfig {
  max_file_size_mb: number;
  max_per_entry: number;
}

export async function adminLogin(username: string, password: string) {
  const response = await adminClient.post<{ code: number; data: { token: string } }>('/login', {
    username,
    password,
  });
  setAdminToken((response as { code: number; data: { token: string } }).data.token);
  return response;
}

export async function adminLogout() {
  await adminClient.post('/logout');
  setAdminToken(null);
}

export function getDashboard() {
  return adminClient.get<{ code: number; data: DashboardData }>('/dashboard');
}

export function getUsers() {
  return adminClient.get<{ code: number; data: AdminUser[] }>('/users');
}

export function deleteUser(id: string) {
  return adminClient.delete(`/users/${id}`);
}

export function updateKdfConfig(algorithm: string, iterations: number) {
  return adminClient.put('/config/kdf', { algorithm, iterations });
}

export function updateRateLimit(endpoint: string, limit: number) {
  return adminClient.put('/config/rate-limit', { endpoint, limit });
}

export function updateAttachmentConfig(maxFileSizeMb: number, maxPerEntry: number) {
  return adminClient.put('/config/attachments', { maxFileSizeMb, maxPerEntry });
}
