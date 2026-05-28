import { getDB } from '../db/index';

const FAILURE_KEY = 'login-failures';

interface FailureRecord {
  key: string;
  count: number;
  lastFailedAt: number;
}

async function readRecord(): Promise<FailureRecord> {
  try {
    const db = await getDB();
    const record = await db.get('loginFailures', FAILURE_KEY);
    if (record) return record as FailureRecord;
  } catch {
    // fallback
  }
  return { key: FAILURE_KEY, count: 0, lastFailedAt: 0 };
}

async function writeRecord(record: FailureRecord): Promise<void> {
  try {
    const db = await getDB();
    await db.put('loginFailures', record);
  } catch {
    // silently fail
  }
}

export async function resetLoginFailures(): Promise<void> {
  try {
    const db = await getDB();
    await db.delete('loginFailures', FAILURE_KEY);
  } catch {
    // silently fail
  }
}

export async function recordLoginFailure(): Promise<void> {
  const record = await readRecord();
  record.count += 1;
  record.lastFailedAt = Date.now();
  await writeRecord(record);
}

export async function getLoginCooldownSeconds(): Promise<number> {
  const record = await readRecord();
  if (record.count < 5) return 0;

  const cooldownMinutes = record.count - 4;
  const cooldownMs = cooldownMinutes * 60 * 1000;
  const elapsed = Date.now() - record.lastFailedAt;

  if (elapsed >= cooldownMs) return 0;

  return Math.ceil((cooldownMs - elapsed) / 1000);
}

export async function getLoginFailureCount(): Promise<number> {
  const record = await readRecord();
  return record.count;
}
