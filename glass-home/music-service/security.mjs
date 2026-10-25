import { timingSafeEqual } from 'node:crypto';
export function authorized(value, token) {
  const a = Buffer.from(value || ''), b = Buffer.from('Bearer ' + token);
  return a.length === b.length && timingSafeEqual(a, b);
}
export function cdnUrl(value) {
  const u = new URL(value);
  if (!/^https?:$/.test(u.protocol) || u.username || u.password || u.port ||
      !/(^|\.)music\.(126\.net|163\.com)$/.test(u.hostname)) throw Error('UNEXPECTED_CDN');
  u.protocol = 'https:';
  return u;
}
export function pageOffset(value) {
  const n = Number(value || 0);
  if (!Number.isSafeInteger(n) || n < 0 || n > 100000) throw Error('INVALID_OFFSET');
  return n;
}
