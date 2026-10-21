import { EventEmitter } from 'node:events';

export const SESSION_FLAG = 1n << 63n;
export const CONFIG_FLAG = 1n << 62n;
export const KEY_FLAG = 1n << 61n;
export const PTS_MASK = KEY_FLAG - 1n;

/** scrcpy 5.0.1: dummy byte, codec id, then session headers or length-prefixed video packets. */
export class VideoParser extends EventEmitter {
  buffer = Buffer.alloc(0);
  stage = 'dummy';
  push(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (true) {
      if (this.stage === 'dummy') {
        if (!this.buffer.length) return;
        if (this.buffer[0] !== 0) throw new Error('Invalid scrcpy handshake');
        this.buffer = this.buffer.subarray(1); this.stage = 'codec';
      }
      if (this.stage === 'codec') {
        if (this.buffer.length < 4) return;
        const codec = this.buffer.subarray(0, 4).toString('ascii');
        if (codec !== 'h264') throw new Error(`Unsupported device codec: ${codec}`);
        this.buffer = this.buffer.subarray(4); this.stage = 'packets';
        this.emit('codec', codec);
      }
      if (this.buffer.length < 12) return;
      const flags = this.buffer.readBigUInt64BE(0);
      if (flags & SESSION_FLAG) {
        const width = this.buffer.readUInt32BE(4), height = this.buffer.readUInt32BE(8);
        if (!width || !height || width > 8192 || height > 8192) throw new Error('Invalid video dimensions');
        this.buffer = this.buffer.subarray(12); this.emit('session', { width, height });
        continue;
      }
      const size = this.buffer.readUInt32BE(8);
      if (size > 8 * 1024 * 1024) throw new Error('Video packet exceeds 8 MiB');
      if (this.buffer.length < 12 + size) return;
      const packet = Buffer.from(this.buffer.subarray(0, 12 + size));
      this.buffer = this.buffer.subarray(12 + size);
      this.emit('packet', packet, !!(flags & CONFIG_FLAG));
    }
  }
}

export function validateTarget(ip, port = 5555) {
  if (typeof ip !== 'string' || !/^\d{1,3}(\.\d{1,3}){3}$/.test(ip)) throw new Error('请输入有效的局域网 IPv4 地址');
  const parts = ip.split('.').map(Number);
  if (parts.some(v => v > 255)) throw new Error('IP 地址的每一段必须在 0–255 之间');
  const [a, b] = parts;
  if (!(a === 10 || a === 127 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31))) throw new Error('只支持本机或局域网电视地址');
  port = Number(port);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('ADB 端口无效');
  return `${ip}:${port}`;
}
