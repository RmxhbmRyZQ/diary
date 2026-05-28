import { getDB } from './index';

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

export async function getAllEntries(): Promise<CachedEntry[]> {
  const db = await getDB();
  return db.getAll('entries');
}

export async function getEntryById(diaryId: string): Promise<CachedEntry | undefined> {
  const db = await getDB();
  return db.get('entries', diaryId);
}

export async function putEntry(entry: CachedEntry): Promise<void> {
  const db = await getDB();
  await db.put('entries', entry);
}

export async function putEntries(entries: CachedEntry[]): Promise<void> {
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

export async function getEntriesByDateRange(start: string, end: string): Promise<CachedEntry[]> {
  const db = await getDB();
  const all = await db.getAll('entries');
  return all.filter((e) => e.diaryDate >= start && e.diaryDate <= end);
}

export async function getFavoriteEntries(): Promise<CachedEntry[]> {
  const db = await getDB();
  return db.getAllFromIndex('entries', 'favorite', 1);
}

export async function getEntryByDiaryDate(diaryDate: string): Promise<CachedEntry | undefined> {
  const db = await getDB();
  return db.getFromIndex('entries', 'diaryDate', diaryDate);
}

export async function clearAllEntries(): Promise<void> {
  const db = await getDB();
  await db.clear('entries');
}

export function buildEntryFromDecrypted(
  id: string,
  diaryDate: string,
  mood: string | null,
  weather: string | null,
  favorite: boolean,
  version: number,
  updatedAt: string,
  decryptedPayload: { title: string; content: string; tags: string[]; attachmentIds: string[] },
): CachedEntry {
  return {
    diaryId: id,
    diaryDate,
    title: decryptedPayload.title || '',
    content: decryptedPayload.content || '',
    summary: extractSummary(decryptedPayload.content || ''),
    tags: decryptedPayload.tags || [],
    mood,
    weather,
    favorite,
    attachmentIds: decryptedPayload.attachmentIds || [],
    serverUpdatedAt: updatedAt,
    serverVersion: version,
  };
}
