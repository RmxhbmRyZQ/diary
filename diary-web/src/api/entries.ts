import apiClient from './client';

export interface SyncEntry {
  id: string;
  diaryDate: string;
  updatedAt: string;
}

export interface EntryDetail {
  id: string;
  diaryDate: string;
  mood: string | null;
  weather: string | null;
  favorite: boolean;
  encryptedPayload: string;
  iv: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEntryParams {
  diaryDate: string;
  mood?: string;
  weather?: string;
  favorite?: boolean;
  encryptedPayload: string;
  iv: string;
  attachmentIds?: string[];
}

export interface UpdateEntryParams {
  diaryDate: string;
  mood?: string;
  weather?: string;
  favorite?: boolean;
  encryptedPayload: string;
  iv: string;
  version: number;
  attachmentIds?: string[];
}

export interface UpdateEntryMetaParams {
  mood?: string;
  weather?: string;
  favorite?: boolean;
  diaryDate?: string;
  version: number;
}

export function syncEntries() {
  return apiClient.get<{ code: number; data: { entries: SyncEntry[] } }>('/entries/sync');
}

export function batchGetEntries(ids: string[]) {
  return apiClient.get<{ code: number; data: { entries: EntryDetail[] } }>('/entries/batch', {
    params: { ids: ids.join(',') },
  });
}

export function createEntry(params: CreateEntryParams) {
  return apiClient.post<{ code: number; data: EntryDetail }>('/entries', params);
}

export function updateEntry(id: string, params: UpdateEntryParams) {
  return apiClient.put<{ code: number; data: EntryDetail }>(`/entries/${id}`, params);
}

export function updateEntryMeta(id: string, params: UpdateEntryMetaParams) {
  return apiClient.patch<{ code: number; data: EntryDetail }>(`/entries/${id}/meta`, params);
}

export function deleteEntry(id: string) {
  return apiClient.delete(`/entries/${id}`);
}
