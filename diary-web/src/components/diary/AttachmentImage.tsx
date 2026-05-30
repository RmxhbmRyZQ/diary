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

  useEffect(() => {
    if (!dek) return;
    let cancelled = false;

    (async () => {
      try {
        const { data, sha256, iv: headerIv, contentType } = await downloadAttachment(attachmentId);

        // Verify SHA-256
        if (sha256) {
          const computedHash = await crypto.subtle.digest('SHA-256', data);
          const computedHex = Array.from(new Uint8Array(computedHash))
            .map((b) => b.toString(16).padStart(2, '0'))
            .join('');
          if (computedHex !== sha256.toLowerCase()) {
            if (!cancelled) setError(true);
            return;
          }
        }

        // Look up the IV from local storage, fall back to response header
        let ivB64 = await getAttachmentIv(attachmentId);
        if (!ivB64 && headerIv) {
          ivB64 = headerIv;
          await putAttachmentIv(attachmentId, headerIv);
        }
        if (!ivB64) {
          if (!cancelled) setError(true);
          return;
        }

        const iv = new Uint8Array(base64ToArrayBuffer(ivB64));

        // Decrypt
        const plaintext = await crypto.subtle.decrypt(
          { name: 'AES-GCM', iv },
          dek,
          data,
        );

        if (!cancelled) {
          const blob = new Blob([plaintext], { type: contentType });
          const url = URL.createObjectURL(blob);
          if (blobUrlRef.current) URL.revokeObjectURL(blobUrlRef.current);
          blobUrlRef.current = url;
          setSrc(url);
        }
      } catch {
        if (!cancelled) setError(true);
      }
    })();

    return () => {
      cancelled = true;
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
