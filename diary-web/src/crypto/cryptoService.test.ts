import { describe, it, expect } from 'vitest';
import {
  generateDEK,
  deriveKey,
  deriveAuthKeyBytes,
  encryptDEK,
  decryptDEK,
  encryptPayload,
  decryptPayload,
  encryptFile,
  decryptFile,
  verifyFileIntegrity,
  generateRandomBytes,
} from './cryptoService';
import { arrayBufferToBase64 } from './utils';

describe('crypto/cryptoService', () => {
  const TEST_PASSWORD = 'testPassword123';
  const SALT = new Uint8Array(16).fill(0x42);
  const ITERATIONS = 1000;

  describe('generateRandomBytes', () => {
    it('should generate bytes of specified length', () => {
      const bytes = generateRandomBytes(32);
      expect(bytes.length).toBe(32);
    });

    it('should generate different values on each call', () => {
      const a = generateRandomBytes(16);
      const b = generateRandomBytes(16);
      let same = true;
      for (let i = 0; i < 16; i++) {
        if (a[i] !== b[i]) {
          same = false;
          break;
        }
      }
      expect(same).toBe(false);
    });
  });

  describe('key derivation', () => {
    it('should derive encryption key successfully', async () => {
      const key = await deriveKey(TEST_PASSWORD, SALT, ITERATIONS, 'encrypt');
      expect(key.type).toBe('secret');
      expect(key.algorithm.name).toBe('AES-GCM');
    });

    it('should derive auth key successfully', async () => {
      const key = await deriveKey(TEST_PASSWORD, SALT, ITERATIONS, 'auth');
      expect(key.type).toBe('secret');
    });

    it('should derive auth key bytes successfully', async () => {
      const bytes = await deriveAuthKeyBytes(TEST_PASSWORD, SALT, ITERATIONS);
      expect(bytes.byteLength).toBe(32);
    });

    it('should produce different keys for "auth" and "encrypt" usage', async () => {
      const testData = new TextEncoder().encode('test data');

      const authKey = await deriveKey(TEST_PASSWORD, SALT, ITERATIONS, 'auth');
      const encKey = await deriveKey(TEST_PASSWORD, SALT, ITERATIONS, 'encrypt');

      const iv = generateRandomBytes(12);
      const authEncrypted = await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv },
        authKey,
        testData,
      );
      const encEncrypted = await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv },
        encKey,
        testData,
      );

      const authBytes = new Uint8Array(authEncrypted);
      const encBytes = new Uint8Array(encEncrypted);

      let same = true;
      for (let i = 0; i < authBytes.length; i++) {
        if (authBytes[i] !== encBytes[i]) {
          same = false;
          break;
        }
      }
      expect(same).toBe(false);
    });
  });

  describe('DEK lifecycle', () => {
    it('should generate a valid DEK', async () => {
      const dek = await generateDEK();
      expect(dek.type).toBe('secret');
    });

    it('should encrypt and decrypt DEK correctly', async () => {
      const dek = await generateDEK();
      const kek = await deriveKey(TEST_PASSWORD, SALT, ITERATIONS, 'encrypt');

      const encrypted = await encryptDEK(dek, kek);
      expect(encrypted.encryptedPayload).toBeTruthy();
      expect(encrypted.iv).toBeTruthy();

      const decryptedDek = await decryptDEK(encrypted.encryptedPayload, kek, encrypted.iv);
      expect(decryptedDek.type).toBe('secret');
    });

    it('should fail to decrypt DEK with wrong KEK', async () => {
      const dek = await generateDEK();
      const kek = await deriveKey(TEST_PASSWORD, SALT, ITERATIONS, 'encrypt');
      const wrongKek = await deriveKey('wrongPassword', SALT, ITERATIONS, 'encrypt');

      const encrypted = await encryptDEK(dek, kek);

      await expect(
        decryptDEK(encrypted.encryptedPayload, wrongKek, encrypted.iv),
      ).rejects.toThrow();
    });
  });

  describe('payload encryption', () => {
    it('should encrypt and decrypt a payload correctly', async () => {
      const dek = await generateDEK();
      const payload = { title: '我的日记', content: '今天天气很好', tags: ['生活'] };

      const encrypted = await encryptPayload(payload, dek);
      expect(encrypted.encryptedPayload).toBeTruthy();
      expect(encrypted.iv).toBeTruthy();

      const decrypted = await decryptPayload(encrypted.encryptedPayload, encrypted.iv, dek);
      expect(decrypted).toEqual(payload);
    });

    it('should produce different ciphertext for same payload', async () => {
      const dek = await generateDEK();
      const payload = { title: '重复加密测试' };

      const encrypted1 = await encryptPayload(payload, dek);
      const encrypted2 = await encryptPayload(payload, dek);

      expect(encrypted1.encryptedPayload).not.toBe(encrypted2.encryptedPayload);
      expect(encrypted1.iv).not.toBe(encrypted2.iv);
    });

    it('should fail to decrypt with wrong DEK', async () => {
      const dek1 = await generateDEK();
      const dek2 = await generateDEK();
      const payload = { test: 'data' };

      const encrypted = await encryptPayload(payload, dek1);

      await expect(
        decryptPayload(encrypted.encryptedPayload, encrypted.iv, dek2),
      ).rejects.toThrow();
    });
  });

  describe('file encryption', () => {
    it('should encrypt and decrypt a file correctly', async () => {
      const dek = await generateDEK();
      const fileData = new TextEncoder().encode('Hello, this is a test file content!').buffer;

      const encrypted = await encryptFile(fileData, dek);
      expect(encrypted.sha256).toBeTruthy();
      expect(encrypted.sha256.length).toBe(64);

      const decrypted = await decryptFile(encrypted.ciphertext, encrypted.iv, dek);
      expect(new Uint8Array(decrypted)).toEqual(new Uint8Array(fileData));
    });

    it('should verify file integrity correctly', async () => {
      const dek = await generateDEK();
      const fileData = new TextEncoder().encode('Integrity test content').buffer;

      const encrypted = await encryptFile(fileData, dek);
      const valid = await verifyFileIntegrity(encrypted.ciphertext, encrypted.sha256);
      expect(valid).toBe(true);
    });

    it('should fail integrity check with wrong hash', async () => {
      const dek = await generateDEK();
      const fileData = new TextEncoder().encode('Another test').buffer;

      const encrypted = await encryptFile(fileData, dek);
      const valid = await verifyFileIntegrity(encrypted.ciphertext, '0'.repeat(64));
      expect(valid).toBe(false);
    });
  });

  describe('end-to-end', () => {
    it('should simulate full encryption workflow', async () => {
      const password = 'userPassword!';
      const saltAuth = generateRandomBytes(16);
      const saltEnc = generateRandomBytes(16);
      const iterations = 1000;

      const kek = await deriveKey(password, saltEnc, iterations, 'encrypt');
      const authKey = await deriveAuthKeyBytes(password, saltAuth, iterations);
      expect(authKey.byteLength).toBe(32);

      const dek = await generateDEK();
      const { encryptedPayload: encryptedDek, iv: dekIv } = await encryptDEK(dek, kek);

      const restoredDek = await decryptDEK(encryptedDek, kek, dekIv);

      const diary = {
        title: '完整的端到端测试',
        content: '## 今天\n一切都好',
        tags: ['测试', '加密'],
        attachmentIds: [],
      };

      const { encryptedPayload, iv } = await encryptPayload(diary, restoredDek);
      const decrypted = await decryptPayload(encryptedPayload, iv, restoredDek);
      expect(decrypted).toEqual(diary);
    });
  });
});
