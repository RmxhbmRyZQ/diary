import { describe, it, expect } from 'vitest';
import {
  arrayBufferToBase64,
  base64ToArrayBuffer,
  stringToArrayBuffer,
  arrayBufferToString,
  hexToArrayBuffer,
  arrayBufferToHex,
  concatArrayBuffers,
} from './utils';

describe('crypto/utils', () => {
  describe('arrayBufferToBase64 / base64ToArrayBuffer', () => {
    it('should round-trip an ArrayBuffer through Base64 correctly', () => {
      const original = new Uint8Array([0, 1, 2, 128, 255, 77, 99, 200]).buffer;
      const base64 = arrayBufferToBase64(original);
      const restored = base64ToArrayBuffer(base64);
      expect(new Uint8Array(restored)).toEqual(new Uint8Array(original));
    });

    it('should handle an empty buffer', () => {
      const original = new ArrayBuffer(0);
      const base64 = arrayBufferToBase64(original);
      expect(base64).toBe('');
      const restored = base64ToArrayBuffer(base64);
      expect(restored.byteLength).toBe(0);
    });

    it('should produce correct Base64 for known input', () => {
      const buffer = new Uint8Array([102, 111, 111]).buffer;
      expect(arrayBufferToBase64(buffer)).toBe('Zm9v');
    });
  });

  describe('stringToArrayBuffer / arrayBufferToString', () => {
    it('should round-trip a string correctly', () => {
      const original = '你好，世界！Hello World! 🎉';
      const buffer = stringToArrayBuffer(original);
      const restored = arrayBufferToString(buffer);
      expect(restored).toBe(original);
    });

    it('should handle empty string', () => {
      const buffer = stringToArrayBuffer('');
      expect(buffer.byteLength).toBe(0);
      expect(arrayBufferToString(buffer)).toBe('');
    });
  });

  describe('hexToArrayBuffer / arrayBufferToHex', () => {
    it('should round-trip correctly', () => {
      const original = new Uint8Array([0x00, 0xff, 0x7a, 0x3c, 0xab, 0x01]).buffer;
      const hex = arrayBufferToHex(original);
      const restored = hexToArrayBuffer(hex);
      expect(new Uint8Array(restored)).toEqual(new Uint8Array(original));
    });

    it('should produce lowercase hex', () => {
      const buffer = new Uint8Array([0xab, 0xcd, 0xef]).buffer;
      expect(arrayBufferToHex(buffer)).toBe('abcdef');
    });

    it('should pad single hex digits', () => {
      const buffer = new Uint8Array([0x0a, 0x01]).buffer;
      expect(arrayBufferToHex(buffer)).toBe('0a01');
    });
  });

  describe('concatArrayBuffers', () => {
    it('should concatenate two buffers', () => {
      const a = new Uint8Array([1, 2, 3]).buffer;
      const b = new Uint8Array([4, 5, 6]).buffer;
      const result = concatArrayBuffers(a, b);
      expect(new Uint8Array(result)).toEqual(new Uint8Array([1, 2, 3, 4, 5, 6]));
    });

    it('should handle empty first buffer', () => {
      const a = new ArrayBuffer(0);
      const b = new Uint8Array([7, 8]).buffer;
      const result = concatArrayBuffers(a, b);
      expect(new Uint8Array(result)).toEqual(new Uint8Array([7, 8]));
    });

    it('should handle empty second buffer', () => {
      const a = new Uint8Array([9, 10]).buffer;
      const b = new ArrayBuffer(0);
      const result = concatArrayBuffers(a, b);
      expect(new Uint8Array(result)).toEqual(new Uint8Array([9, 10]));
    });
  });
});
