const MAX_WIDTH = 1920;
const MAX_HEIGHT = 1920;
const JPEG_QUALITY = 0.8;
const MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5MB fallback threshold

export async function compressImage(file: File): Promise<File> {
  if (!file.type.startsWith('image/')) {
    return file;
  }

  // Skip GIF/APNG/animated formats — canvas can't preserve animation
  if (file.type === 'image/gif') {
    return file;
  }

  const bitmap = await createImageBitmap(file);
  const { width, height } = bitmap;

  // If already small, only re-encode to strip metadata
  let targetWidth = width;
  let targetHeight = height;
  if (width > MAX_WIDTH || height > MAX_HEIGHT) {
    const ratio = Math.min(MAX_WIDTH / width, MAX_HEIGHT / height);
    targetWidth = Math.round(width * ratio);
    targetHeight = Math.round(height * ratio);
  }

  const canvas = document.createElement('canvas');
  canvas.width = targetWidth;
  canvas.height = targetHeight;
  const ctx = canvas.getContext('2d')!;
  ctx.drawImage(bitmap, 0, 0, targetWidth, targetHeight);
  bitmap.close();

  const outputType = file.type === 'image/png' ? 'image/png' : 'image/jpeg';

  let blob = await new Promise<Blob | null>((resolve) =>
    canvas.toBlob(resolve, outputType, outputType === 'image/jpeg' ? JPEG_QUALITY : undefined),
  );

  if (!blob) {
    return file;
  }

  // If still too large, reduce quality further
  if (outputType === 'image/jpeg' && blob.size > MAX_SIZE_BYTES) {
    blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, 'image/jpeg', 0.5),
    );
    if (!blob) return file;
  }

  const ext = outputType === 'image/jpeg' ? 'jpg' : 'png';
  return new File([blob], file.name.replace(/\.[^.]+$/, `.${ext}`), {
    type: outputType,
    lastModified: file.lastModified,
  });
}
