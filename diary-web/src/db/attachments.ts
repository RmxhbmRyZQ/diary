import { getDB } from './index';

export interface AttachmentIvRecord {
  attachmentId: string;
  ivB64: string;
}

export async function putAttachmentIv(attachmentId: string, ivB64: string): Promise<void> {
  const db = await getDB();
  await db.put('attachmentIv', { attachmentId, ivB64 });
}

export async function getAttachmentIv(attachmentId: string): Promise<string | undefined> {
  const db = await getDB();
  const record = await db.get('attachmentIv', attachmentId);
  return record?.ivB64;
}
