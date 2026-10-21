export const TV_APPS = Object.freeze({
  bilibili: { name: '云视听小电视', package: 'com.xiaodianshi.tv.yst', component: 'com.xiaodianshi.tv.yst/.ui.main.MainActivity' },
  music: { name: '网易云音乐', package: 'com.netease.cloudmusic.tv', component: 'com.netease.cloudmusic.tv/com.netease.cloudmusic.app.LoadingActivity' },
  cast: { name: '无线投屏', package: 'com.xiaomi.mitv.smartshare', component: 'com.xiaomi.mitv.smartshare/.MainActivity' },
  netflix: { name: 'Netflix', package: 'com.netflix.ninja', component: null },
});
export function resolveApp(id) {
  if (typeof id !== 'string' || !Object.hasOwn(TV_APPS, id)) throw new Error('请选择主页中的应用');
  return TV_APPS[id];
}

// Keep only the last waiting intent while an ADB launch is in flight.
export class LatestLaunchQueue {
  running = false;
  pending = null;
  constructor(execute) { this.execute = execute; }
  request(value) {
    return new Promise((resolve, reject) => {
      this.pending?.resolve({ skipped: true });
      this.pending = { value, resolve, reject };
      void this.drain();
    });
  }
  cancel() { this.pending?.resolve({ skipped: true }); this.pending = null; }
  async drain() {
    if (this.running) return;
    this.running = true;
    try {
      while (this.pending) {
        const job = this.pending; this.pending = null;
        try { job.resolve(await this.execute(job.value)); } catch (e) { job.reject(e); }
      }
    } finally { this.running = false; }
  }
}
