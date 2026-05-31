import { getDB } from './index';
import { decryptPayload } from '../crypto/cryptoService';

export interface EncryptedCachedEntry {
  diaryId: string;
  diaryDate: string;
  encryptedPayload: string;
  iv: string;
  mood: string | null;
  weather: string | null;
  favorite: boolean;
  serverUpdatedAt: string;
  serverVersion: number;
}

export interface CachedEntry {
  diaryId: string;
  diaryDate: string;
  title: string;
  content: string;
  summary: string;
  tags: string[];
  mood: string | null;
  weather: string | null;
  favorite: boolean;
  attachmentIds: string[];
  serverUpdatedAt: string;
  serverVersion: number;
}

function extractSummary(content: string, maxLen = 50): string {
  const plain = content.replace(/[#*`\[\]!>|\\-]/g, '').replace(/\s+/g, ' ').trim();
  return plain.length > maxLen ? plain.substring(0, maxLen) + '...' : plain;
}

export async function decryptCachedEntry(
  encrypted: EncryptedCachedEntry,
  dek: CryptoKey,
): Promise<CachedEntry> {
  const decrypted = await decryptPayload(encrypted.encryptedPayload, encrypted.iv, dek);
  const payload = decrypted as { title: string; content: string; tags: string[]; attachmentIds: string[] };
  return {
    diaryId: encrypted.diaryId,
    diaryDate: encrypted.diaryDate,
    title: payload.title || '',
    content: payload.content || '',
    summary: extractSummary(payload.content || ''),
    tags: payload.tags || [],
    mood: encrypted.mood,
    weather: encrypted.weather,
    favorite: encrypted.favorite,
    attachmentIds: payload.attachmentIds || [],
    serverUpdatedAt: encrypted.serverUpdatedAt,
    serverVersion: encrypted.serverVersion,
  };
}

export function buildEncryptedEntry(
  id: string,
  diaryDate: string,
  mood: string | null,
  weather: string | null,
  favorite: boolean,
  version: number,
  updatedAt: string,
  encryptedPayload: string,
  iv: string,
): EncryptedCachedEntry {
  return {
    diaryId: id,
    diaryDate,
    encryptedPayload,
    iv,
    mood,
    weather,
    favorite,
    serverUpdatedAt: updatedAt,
    serverVersion: version,
  };
}

export async function getAllEntries(): Promise<EncryptedCachedEntry[]> {
  const db = await getDB();
  return db.getAll('entries');
}

export async function getEntryById(diaryId: string): Promise<EncryptedCachedEntry | undefined> {
  const db = await getDB();
  return db.get('entries', diaryId);
}

export async function putEntry(entry: EncryptedCachedEntry): Promise<void> {
  const db = await getDB();
  await db.put('entries', entry);
}

export async function putEntries(entries: EncryptedCachedEntry[]): Promise<void> {
  const db = await getDB();
  const tx = db.transaction('entries', 'readwrite');
  for (const entry of entries) {
    await tx.store.put(entry);
  }
  await tx.done;
}

export async function deleteEntry(diaryId: string): Promise<void> {
  const db = await getDB();
  await db.delete('entries', diaryId);
}

export async function deleteEntriesNotIn(ids: Set<string>): Promise<void> {
  const db = await getDB();
  const all = await db.getAllKeys('entries');
  const tx = db.transaction('entries', 'readwrite');
  for (const key of all) {
    if (!ids.has(key as string)) {
      await tx.store.delete(key);
    }
  }
  await tx.done;
}

export async function getEntriesByDateRange(start: string, end: string): Promise<EncryptedCachedEntry[]> {
  const db = await getDB();
  const all = await db.getAll('entries');
  return all.filter((e) => e.diaryDate >= start && e.diaryDate <= end);
}

export async function getFavoriteEntries(): Promise<EncryptedCachedEntry[]> {
  const db = await getDB();
  return db.getAllFromIndex('entries', 'favorite', 1);
}

export async function getEntryByDiaryDate(diaryDate: string): Promise<EncryptedCachedEntry | undefined> {
  const db = await getDB();
  return db.getFromIndex('entries', 'diaryDate', diaryDate);
}

export async function clearAllEntries(): Promise<void> {
  const db = await getDB();
  await db.clear('entries');
}
