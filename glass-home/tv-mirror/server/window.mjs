const LABELS = { 'com.xiaodianshi.tv.yst': '云视听小电视', 'com.netease.cloudmusic.tv': '网易云音乐', 'com.xiaomi.mitv.smartshare': '无线投屏', 'com.netflix.ninja': 'Netflix' };
export function protectedWindow(dump) {
  for (const window of dump.split(/\n\s*Window #\d+ /)) {
    if (!/mDisplayId=0\b/.test(window) || !/isVisible=true\b/.test(window) || !/isOnScreen=true\b/.test(window)) continue;
    const flags = window.match(/\bfl=([^\r\n]*)/)?.[1]?.trim().split(/\s+/) || [];
    if (!flags.includes('SECURE')) continue;
    const pkg = window.match(/\bpackage=([\w.]+)/)?.[1];
    return { protected: true, protectedApp: LABELS[pkg] || '当前应用' };
  }
  return { protected: false, protectedApp: '' };
}
