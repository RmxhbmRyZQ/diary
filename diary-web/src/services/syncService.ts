import { syncEntries, batchGetEntries, type SyncEntry } from '../api/entries';
import { decryptPayload } from '../crypto/cryptoService';
import {
  getAllEntries,
  putEntry,
  deleteEntriesNotIn,
  buildEntryFromDecrypted,
  type CachedEntry,
} from '../db/entries';

export interface SyncResult {
  added: number;
  updated: number;
  removed: number;
}

export async function fullSync(dek: CryptoKey): Promise<SyncResult> {
  const response = await syncEntries();
  const serverEntries: SyncEntry[] = response.data.entries;

  const localEntries = await getAllEntries();
  const localMap = new Map(localEntries.map((e) => [e.diaryId, e]));

  const serverIds = new Set(serverEntries.map((e) => e.id));

  const needFetch: string[] = [];
  let added = 0;
  let updated = 0;

  for (const se of serverEntries) {
    const local = localMap.get(se.id);
    if (!local) {
      needFetch.push(se.id);
      added++;
    } else if (local.serverUpdatedAt < se.updatedAt) {
      needFetch.push(se.id);
      updated++;
    }
  }

  const removed = localEntries.filter((e) => !serverIds.has(e.diaryId)).length;
  await deleteEntriesNotIn(serverIds);

  if (needFetch.length > 0) {
    const batchSize = 50;
    for (let i = 0; i < needFetch.length; i += batchSize) {
      const batch = needFetch.slice(i, i + batchSize);
      const batchResponse = await batchGetEntries(batch);
      const details = batchResponse.data.entries;

      for (const detail of details) {
        try {
          const decrypted = await decryptPayload(detail.encryptedPayload, detail.iv, dek);
          const cached = buildEntryFromDecrypted(
            detail.id,
            detail.diaryDate,
            detail.mood,
            detail.weather,
            detail.favorite,
            detail.version,
            detail.updatedAt,
            decrypted as { title: string; content: string; tags: string[]; attachmentIds: string[] },
          );
          await putEntry(cached);
        } catch {
          // skip entries we can't decrypt
        }
      }
    }
  }

  return { added, updated, removed };
}

export function getSyncSummary(entries: CachedEntry[]): SyncEntry[] {
  return entries.map((e) => ({
    id: e.diaryId,
    diaryDate: e.diaryDate,
    updatedAt: e.serverUpdatedAt,
  }));
}
