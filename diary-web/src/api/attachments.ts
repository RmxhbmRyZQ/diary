import axios from 'axios';
import apiClient from './client';

export interface AttachmentUploadResponse {
  id: string;
  mime_type: string;
  sha256: string;
  created_at: string;
}

export function uploadAttachment(diaryId: string, file: Blob, iv: string, sha256: string) {
  const formData = new FormData();
  formData.append('diary_id', diaryId);
  formData.append('file', file);
  formData.append('iv', iv);
  formData.append('sha256', sha256);

  return apiClient.post<{ code: number; data: AttachmentUploadResponse }>('/attachments', formData);
}

export async function downloadAttachment(id: string): Promise<{ data: ArrayBuffer; sha256: string; contentType: string }> {
  try {
    const response = await axios.get(`/api/v1/attachments/${id}`, {
      responseType: 'arraybuffer',
      withCredentials: true,
    });
    const sha256 = (response.headers['x-content-sha256'] as string) || '';
    const contentType = (response.headers['content-type'] as string) || 'application/octet-stream';
    return { data: response.data as ArrayBuffer, sha256, contentType };
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      window.dispatchEvent(new CustomEvent('auth:session-expired'));
    }
    throw error;
  }
}

export function deleteAttachment(id: string) {
  return apiClient.delete(`/attachments/${id}`);
}
