import { arrayBufferToBase64, base64ToArrayBuffer, stringToArrayBuffer, arrayBufferToString, arrayBufferToHex } from './utils';

const ENCRYPTION_ALGORITHM = 'AES-GCM';
const KEY_LENGTH = 256;
const IV_LENGTH = 12;
const KDF_ALGORITHM = 'PBKDF2';
const KDF_HASH = 'SHA-256';

export interface KdfParams {
  algorithm: string;
  iterations: number;
}

export interface EncryptedPayload {
  encryptedPayload: string;
  iv: string;
}

export interface EncryptedFile {
  ciphertext: ArrayBuffer;
  iv: string;
  sha256: string;
}

function generateRandomBytes(length: number): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(length));
}

export async function deriveKey(
  password: string,
  salt: Uint8Array,
  iterations: number,
  usage: 'auth' | 'encrypt',
): Promise<CryptoKey> {
  const labeledPassword = `diary-${usage}-key:${password}`;
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    stringToArrayBuffer(labeledPassword),
    KDF_ALGORITHM,
    false,
    ['deriveBits', 'deriveKey'],
  );

  return crypto.subtle.deriveKey(
    {
      name: KDF_ALGORITHM,
      salt,
      iterations,
      hash: KDF_HASH,
    },
    keyMaterial,
    {
      name: ENCRYPTION_ALGORITHM,
      length: KEY_LENGTH,
    },
    false,
    usage === 'auth' ? ['encrypt'] : ['encrypt', 'decrypt', 'wrapKey', 'unwrapKey'],
  );
}

export async function deriveAuthKeyBytes(
  password: string,
  salt: Uint8Array,
  iterations: number,
): Promise<ArrayBuffer> {
  const labeledPassword = `diary-auth-key:${password}`;
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    stringToArrayBuffer(labeledPassword),
    KDF_ALGORITHM,
    false,
    ['deriveBits'],
  );

  return crypto.subtle.deriveBits(
    {
      name: KDF_ALGORITHM,
      salt,
      iterations,
      hash: KDF_HASH,
    },
    keyMaterial,
    256,
  );
}

export async function generateDEK(): Promise<CryptoKey> {
  return crypto.subtle.generateKey(
    {
      name: ENCRYPTION_ALGORITHM,
      length: KEY_LENGTH,
    },
    true,
    ['encrypt', 'decrypt'],
  );
}

export async function encryptDEK(dek: CryptoKey, kek: CryptoKey): Promise<EncryptedPayload> {
  const iv = generateRandomBytes(IV_LENGTH);
  const wrappedKey = await crypto.subtle.wrapKey(
    'raw',
    dek,
    kek,
    { name: ENCRYPTION_ALGORITHM, iv },
  );

  return {
    encryptedPayload: arrayBufferToBase64(wrappedKey),
    iv: arrayBufferToBase64(iv),
  };
}

export async function decryptDEK(
  encryptedDekBase64: string,
  kek: CryptoKey,
  ivBase64: string,
): Promise<CryptoKey> {
  const iv = new Uint8Array(base64ToArrayBuffer(ivBase64));
  const encryptedDek = base64ToArrayBuffer(encryptedDekBase64);

  return crypto.subtle.unwrapKey(
    'raw',
    encryptedDek,
    kek,
    { name: ENCRYPTION_ALGORITHM, iv },
    { name: ENCRYPTION_ALGORITHM, length: KEY_LENGTH },
    true,
    ['encrypt', 'decrypt'],
  );
}

export async function encryptPayload(
  plaintext: Record<string, unknown>,
  dek: CryptoKey,
): Promise<EncryptedPayload> {
  const iv = generateRandomBytes(IV_LENGTH);
  const json = JSON.stringify(plaintext);
  const data = stringToArrayBuffer(json);

  const ciphertext = await crypto.subtle.encrypt(
    { name: ENCRYPTION_ALGORITHM, iv },
    dek,
    data,
  );

  return {
    encryptedPayload: arrayBufferToBase64(ciphertext),
    iv: arrayBufferToBase64(iv),
  };
}

export async function decryptPayload(
  encryptedPayloadBase64: string,
  ivBase64: string,
  dek: CryptoKey,
): Promise<Record<string, unknown>> {
  const iv = new Uint8Array(base64ToArrayBuffer(ivBase64));
  const ciphertext = base64ToArrayBuffer(encryptedPayloadBase64);

  const plaintext = await crypto.subtle.decrypt(
    { name: ENCRYPTION_ALGORITHM, iv },
    dek,
    ciphertext,
  );

  const json = arrayBufferToString(plaintext);
  return JSON.parse(json);
}

export async function encryptFile(
  fileBuffer: ArrayBuffer,
  dek: CryptoKey,
): Promise<EncryptedFile> {
  const iv = generateRandomBytes(IV_LENGTH);

  const ciphertext = await crypto.subtle.encrypt(
    { name: ENCRYPTION_ALGORITHM, iv },
    dek,
    fileBuffer,
  );

  const sha256Hash = await crypto.subtle.digest('SHA-256', ciphertext);
  const sha256 = Array.from(new Uint8Array(sha256Hash))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');

  return {
    ciphertext,
    iv: arrayBufferToBase64(iv),
    sha256,
  };
}

export async function decryptFile(
  ciphertext: ArrayBuffer,
  ivBase64: string,
  dek: CryptoKey,
): Promise<ArrayBuffer> {
  const iv = new Uint8Array(base64ToArrayBuffer(ivBase64));

  return crypto.subtle.decrypt(
    { name: ENCRYPTION_ALGORITHM, iv },
    dek,
    ciphertext,
  );
}

export async function verifyFileIntegrity(
  ciphertext: ArrayBuffer,
  expectedSha256: string,
): Promise<boolean> {
  const hash = await crypto.subtle.digest('SHA-256', ciphertext);
  const actualSha256 = Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
  return actualSha256 === expectedSha256;
}

export async function hashAuthKey(authKeyBytes: ArrayBuffer): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', authKeyBytes);
  return arrayBufferToHex(hash);
}

export { generateRandomBytes };
