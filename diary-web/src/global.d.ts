// TS 5.9+ compatibility: Uint8Array.buffer is widened to ArrayBufferLike;
// restore ArrayBuffer type for Web Crypto API BufferSource compat.
declare global {
  interface Uint8Array {
    readonly buffer: ArrayBuffer;
  }
}
export {};
