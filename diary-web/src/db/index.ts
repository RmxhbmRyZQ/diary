import { openDB, type IDBPDatabase } from 'idb';

const DB_NAME = 'secretDiaryDB';
const DB_VERSION = 1;

let dbPromise: Promise<IDBPDatabase> | null = null;

export function getDB(): Promise<IDBPDatabase> {
  if (!dbPromise) {
    dbPromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(db) {
        if (!db.objectStoreNames.contains('entries')) {
          const store = db.createObjectStore('entries', { keyPath: 'diaryId' });
          store.createIndex('diaryDate', 'diaryDate');
          store.createIndex('favorite', 'favorite');
          store.createIndex('mood', 'mood');
          store.createIndex('weather', 'weather');
          store.createIndex('serverUpdatedAt', 'serverUpdatedAt');
        }

        if (!db.objectStoreNames.contains('loginFailures')) {
          db.createObjectStore('loginFailures', { keyPath: 'key' });
        }

        if (!db.objectStoreNames.contains('attachmentIv')) {
          db.createObjectStore('attachmentIv', { keyPath: 'attachmentId' });
        }
      },
    });
  }
  return dbPromise;
}

export async function clearAllData(): Promise<void> {
  const db = await getDB();
  const storeNames = db.objectStoreNames;
  const tx = db.transaction([...storeNames], 'readwrite');
  for (const name of storeNames) {
    await tx.objectStore(name).clear();
  }
  await tx.done;
}

export function closeDB() {
  if (dbPromise) {
    dbPromise.then((db) => db.close()).catch(() => {});
    dbPromise = null;
  }
}
