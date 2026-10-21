export function codecFromAnnexB(bytes) {
  for (let i = 0; i + 7 < bytes.length; i++) {
    let offset = 0;
    if (bytes[i] === 0 && bytes[i + 1] === 0) {
      if (bytes[i + 2] === 1) offset = 3;
      else if (bytes[i + 2] === 0 && bytes[i + 3] === 1) offset = 4;
    }
    if (offset && (bytes[i + offset] & 31) === 7) {
      return `avc1.${Array.from(bytes.slice(i + offset + 1, i + offset + 4), n => n.toString(16).padStart(2, '0')).join('')}`;
    }
  }
  throw new Error('视频配置中没有找到 H.264 SPS');
}

export class LiveDecoder {
  constructor(canvas, onFrame, onError) {
    this.canvas = canvas; this.onFrame = onFrame; this.onError = onError;
    this.context = canvas.getContext('2d', { alpha: false, desynchronized: true });
    this.waitingForKey = true; this.configBytes = null; this.decoder = null; this.config = null;
    this.arrival = new Map();
  }
  packet(buffer) {
    const view = new DataView(buffer);
    if (view.byteLength < 12) return;
    const flags = view.getBigUint64(0);
    const data = new Uint8Array(buffer, 12);
    if (flags & (1n << 62n)) {
      this.configBytes = data.slice();
      this.config = { codec: codecFromAnnexB(data), optimizeForLatency: true, hardwareAcceleration: 'prefer-hardware' };
      this.reset(); return;
    }
    if (!this.decoder || this.decoder.state !== 'configured') return;
    const key = !!(flags & (1n << 61n));
    if (this.decoder.decodeQueueSize > 5) { this.reset(); return; }
    if (this.waitingForKey && !key) return;
    let payload = data;
    if (key && this.configBytes) { payload = new Uint8Array(this.configBytes.length + data.length); payload.set(this.configBytes); payload.set(data, this.configBytes.length); }
    this.waitingForKey = false;
    const timestamp = Number(flags & ((1n << 61n) - 1n));
    this.arrival.set(timestamp, performance.now());
    if (this.arrival.size > 120) this.arrival.delete(this.arrival.keys().next().value);
    this.decoder.decode(new EncodedVideoChunk({ type: key ? 'key' : 'delta', timestamp, data: payload }));
  }
  reset() {
    this.decoder?.close(); this.arrival.clear(); this.waitingForKey = true;
    this.decoder = new VideoDecoder({
      output: frame => {
        try {
          if (this.canvas.width !== frame.displayWidth || this.canvas.height !== frame.displayHeight) {
            this.canvas.width = frame.displayWidth; this.canvas.height = frame.displayHeight;
          }
          this.context.drawImage(frame, 0, 0, this.canvas.width, this.canvas.height);
          const received = this.arrival.get(frame.timestamp); this.arrival.delete(frame.timestamp);
          this.onFrame(received == null ? 0 : performance.now() - received);
        } finally { frame.close(); }
      },
      error: error => { this.waitingForKey = true; this.onError(`视频解码失败：${error.message}`); },
    });
    this.decoder.configure(this.config);
  }
  close() { if (this.decoder && this.decoder.state !== 'closed') this.decoder.close(); this.arrival.clear(); }
}
