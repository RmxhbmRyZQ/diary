import axios from 'axios';
import { syncEntries, batchGetEntries, type SyncEntry } from '../api/entries';
import { toBeijingISOString } from '../utils/timeUtils';
import { getDB } from '../db/index';
import {
  getAllEntries,
  putEntry,
  deleteEntriesNotIn,
  buildEncryptedEntry,
  type EncryptedCachedEntry,
} from '../db/entries';

function syncKey(username: string): string {
  return `lastSyncTime:${username}`;
}

export interface SyncResult {
  added: number;
  updated: number;
  removed: number;
}

export class SyncError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'SyncError';
  }
}

function getLastSyncTime(username: string): string | undefined {
  const v = localStorage.getItem(syncKey(username));
  return v || undefined;
}

function saveLastSyncTime(username: string) {
  localStorage.setItem(syncKey(username), toBeijingISOString());
}

export async function fullSync(username: string, userId: string, since?: string): Promise<SyncResult> {
  const effectiveSince = since ?? getLastSyncTime(username);

  let response;
  try {
    response = await syncEntries(effectiveSince);
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.data) {
      const msg = (error.response.data as { message?: string }).message;
      if (msg) {
        throw new SyncError(msg);
      }
    }
    throw new SyncError('同步失败，请检查网络连接');
  }

  const serverEntries: SyncEntry[] = response.data.entries;
  const serverIds = new Set(serverEntries.map((e) => e.id));

  const localEntries = await getAllEntries();
  // Clean up entries from other users on every sync
  const otherUserIds = localEntries.filter((e) => e.userId !== userId);
  if (otherUserIds.length > 0) {
    const db = await getDB();
    const tx = db.transaction('entries', 'readwrite');
    for (const e of otherUserIds) {
      await tx.store.delete(e.diaryId);
    }
    await tx.done;
  }

  const myEntries = localEntries.filter((e) => e.userId === userId);
  const localMap = new Map(myEntries.map((e) => [e.diaryId, e]));

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

  let removed = 0;
  if (!effectiveSince) {
    removed = myEntries.filter((e) => !serverIds.has(e.diaryId)).length;
    await deleteEntriesNotIn(serverIds);
  }

  if (needFetch.length > 0) {
    const batchSize = 50;
    for (let i = 0; i < needFetch.length; i += batchSize) {
      const batch = needFetch.slice(i, i + batchSize);
      const batchResponse = await batchGetEntries(batch);
      const details = batchResponse.data.entries;

      for (const detail of details) {
        const cached = buildEncryptedEntry(
          detail.id,
          userId,
          detail.diaryDate,
          detail.mood,
          detail.weather,
          detail.favorite,
          detail.version,
          detail.updatedAt,
          detail.encryptedPayload,
          detail.iv,
        );
        await putEntry(cached);
      }
    }
  }

  saveLastSyncTime(username);
  return { added, updated, removed };
}

export function getSyncSummary(entries: EncryptedCachedEntry[]): SyncEntry[] {
  return entries.map((e) => ({
    id: e.diaryId,
    diaryDate: e.diaryDate,
    updatedAt: e.serverUpdatedAt,
  }));
}
