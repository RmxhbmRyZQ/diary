import { useState, useEffect, useRef } from 'react';
import { downloadAttachment } from '../../api/attachments';
import { getAttachmentIv, putAttachmentIv } from '../../db/attachments';
import { useAuth } from '../../context/AuthContext';
import { base64ToArrayBuffer } from '../../crypto/utils';

interface AttachmentImageProps {
  attachmentId: string;
  alt?: string;
  className?: string;
}

export default function AttachmentImage({ attachmentId, alt = '', className = '' }: AttachmentImageProps) {
  const { dek } = useAuth();
  const [src, setSrc] = useState<string | null>(null);
  const [error, setError] = useState(false);
  const blobUrlRef = useRef<string | null>(null);
  const lastIdRef = useRef<string | null>(null);

  useEffect(() => {
    if (!dek) return;
    if (lastIdRef.current === attachmentId) return;
    lastIdRef.current = attachmentId;
    const currentId = attachmentId;

    (async () => {
      try {
        const { data, sha256, iv: headerIv, contentType } = await downloadAttachment(currentId);

        if (sha256) {
          const computedHash = await crypto.subtle.digest('SHA-256', data);
          const computedHex = Array.from(new Uint8Array(computedHash))
            .map((b) => b.toString(16).padStart(2, '0'))
            .join('');
          if (computedHex !== sha256.toLowerCase()) {
            if (lastIdRef.current === currentId) setError(true);
            return;
          }
        }

        let ivB64 = await getAttachmentIv(currentId);
        if (!ivB64 && headerIv) {
          ivB64 = headerIv;
          await putAttachmentIv(currentId, headerIv);
        }
        if (!ivB64) {
          if (lastIdRef.current === currentId) setError(true);
          return;
        }

        const iv = new Uint8Array(base64ToArrayBuffer(ivB64));

        const plaintext = await crypto.subtle.decrypt(
          { name: 'AES-GCM', iv },
          dek,
          data,
        );

        if (lastIdRef.current === currentId) {
          const blob = new Blob([plaintext], { type: contentType });
          const url = URL.createObjectURL(blob);
          if (blobUrlRef.current) URL.revokeObjectURL(blobUrlRef.current);
          blobUrlRef.current = url;
          setSrc(url);
        }
      } catch {
        if (lastIdRef.current === currentId) setError(true);
      }
    })();

    return () => {
      if (blobUrlRef.current) {
        URL.revokeObjectURL(blobUrlRef.current);
        blobUrlRef.current = null;
      }
    };
  }, [attachmentId, dek]);

  if (error) {
    return (
      <div className={`flex items-center justify-center bg-gray-100 rounded-lg text-gray-400 text-sm ${className}`}
           style={{ minHeight: 100 }}>
        图片加载失败
      </div>
    );
  }

  if (!src) {
    return (
      <div className={`flex items-center justify-center bg-gray-100 rounded-lg animate-pulse ${className}`}
           style={{ minHeight: 100 }}>
        <span className="text-gray-300 text-sm">加载中...</span>
      </div>
    );
  }

  return <img src={src} alt={alt} className={`max-w-full rounded-lg ${className}`} />;
}
