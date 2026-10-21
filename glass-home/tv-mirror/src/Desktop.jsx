import React, { useEffect, useRef, useState } from 'react';
import { Icon } from './Icons';

const APPS = [
  { id: 'bilibili', name: '云视听小电视', en: 'BILIBILI', line: '看见有趣的世界。', description: '大屏里的小电视，装着你的每一种热爱。', type: '视频 · 云视听小电视' },
  { id: 'music', name: '网易云音乐', en: 'NETEASE CLOUD MUSIC', line: '让音乐，填满此刻。', description: '熟悉的旋律，在客厅慢慢响起。', type: '音乐 · 网易云音乐' },
  { id: 'cast', name: '无线投屏', en: 'SCREEN MIRRORING', line: '小屏精彩，大屏分享。', description: '手机里的照片、影片与故事，一起放大。', type: '连接 · 无线投屏' },
  { id: 'netflix', name: 'Netflix', en: 'NETFLIX', line: '给好故事，留一整晚。', description: '你的下一个精彩故事，从这里开始。', type: '影视 · Netflix' },
];

function AppMark({ id }) {
  if (id === 'bilibili') return <svg viewBox="0 0 140 84" aria-hidden="true" className="bili-mark"><path d="m48 10 11 11m33-11L81 21M35 27h70a6 6 0 0 1 6 6v40a6 6 0 0 1-6 6H35a6 6 0 0 1-6-6V33a6 6 0 0 1 6-6Z"/><path d="m46 43 12 3m36-3-12 3M61 62l9 5 9-5"/></svg>;
  if (id === 'music') return <svg viewBox="0 0 100 100" aria-hidden="true" className="music-mark"><path d="M57 16c-5 11-5 21 0 31 7 16 5 28-7 34-14 7-30-3-29-18 1-15 16-25 30-21m6-26c16 1 26 14 25 28M67 28c5 2 9 7 9 13"/></svg>;
  if (id === 'cast') return <Icon name="cast" size={66}/>;
  return <span className="netflix-word">NETFLIX</span>;
}

export function Desktop({ status, ip, setIp, active, setActive, ready, error, busy, stats, toast, pressedKey, canvas, viewer, supported, live, phaseText, connect, screenshot, fullscreen, remoteKey, keyboardRemote, notify }) {
  const [selected, setSelected] = useState(null), [launching, setLaunching] = useState('');
  const [installed, setInstalled] = useState({}), [panel, setPanel] = useState('');
  const [now, setNow] = useState(new Date());
  const hoverTimer = useRef(null), intent = useRef(0), latestTarget = useRef(null);
  const app = APPS.find(a => a.id === selected);
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => { clearInterval(timer); clearTimeout(hoverTimer.current); ++intent.current; };
  }, []);
  useEffect(() => {
    if (status.phase !== 'streaming') { setInstalled({}); return; }
    let disposed = false;
    fetch('/api/apps').then(r => r.json()).then(data => { if (!disposed) setInstalled(data); }).catch(() => {});
    return () => { disposed = true; };
  }, [status.phase, status.ip]);
  function cancelHover() { clearTimeout(hoverTimer.current); }
  async function launch(id, token = ++intent.current) {
    cancelHover(); setSelected(id);
    if (!live) { notify('请先连接电视'); return; }
    if (installed[id] === false) { setLaunching(''); notify(`${APPS.find(a => a.id === id).name}尚未安装，已为它保留入口`); return; }
    latestTarget.current = id; setLaunching(id);
    try {
      const r = await fetch('/api/app', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id }) });
      const result = await r.json(); if (!r.ok) throw new Error(result.error);
      if (token === intent.current && !result.skipped) setLaunching('');
    } catch (e) { if (token === intent.current) { setLaunching(''); latestTarget.current = null; notify(e.message); } }
  }
  function hover(id) {
    cancelHover(); setSelected(id);
    if (latestTarget.current === id) return;
    const token = ++intent.current;
    hoverTimer.current = setTimeout(() => void launch(id, token), 350);
  }
  function home() {
    cancelHover(); ++intent.current; latestTarget.current = null;
    setSelected(null); setLaunching(''); remoteKey('HOME');
  }
  const greeting = now.getHours() < 6 ? '夜深了，放松片刻' : now.getHours() < 12 ? '早安，开启美好一天' : now.getHours() < 18 ? '午后，慢下来' : '晚上好，欢迎回家';
  return <div className="glass-desktop">
    <video className="aerial" autoPlay muted loop playsInline poster="/media/hawaii.png" aria-hidden="true"><source src="/media/hawaii.mp4" type="video/mp4"/></video>
    <div className="aerial-shade"/>
    <header className="desktop-header">
      <div className="clock-block"><p className="greeting">{greeting}</p><div className="clock-line"><time>{now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })}</time><span className="date">{now.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' })}</span></div></div>
      <nav className="top-actions" aria-label="桌面工具"><button className="glass-pill" onClick={home} disabled={!live}><Icon name="home"/>Glass 主页</button><button className={`glass-pill ${panel === 'remote' ? 'chosen' : ''}`} aria-expanded={panel === 'remote'} onClick={() => { cancelHover(); setPanel(panel === 'remote' ? '' : 'remote'); }}><Icon name="menu"/>遥控器</button><button className={`glass-pill ${panel === 'settings' ? 'chosen' : ''}`} aria-expanded={panel === 'settings'} onClick={() => { cancelHover(); setPanel(panel === 'settings' ? '' : 'settings'); }}><Icon name="wifi"/>连接<span className={`status-dot ${live ? 'online' : ''}`}/></button></nav>
    </header>
    <main className="desktop-main">
      <section className="feature-area" aria-label="应用与电视预览">
        <div className="feature-copy" key={selected || 'home'}><div className="eyebrow">{app?.type || 'GLASS HOME · YOUR LIVING ROOM'}</div><h1>{app?.name || '把时间，留给喜欢的事。'}</h1><p className="feature-line">{app?.line || '你喜欢的，都在这里。'}</p><p className="feature-description">{app?.description || '轻轻停留，即刻在电视打开。'}</p><div className="feature-meta"><span className={`status-dot ${live ? 'online' : ''}`}/>{launching ? `正在电视打开${APPS.find(a => a.id === launching)?.name}…` : selected && installed[selected] === false ? '电视尚未安装 · 入口已预留' : `${phaseText} · 三楼 Redmi X`}<span className="meta-divider"/>{status.quality === '720' ? '720p' : '1080p'}</div></div>
        <section className="preview-shell" aria-label="电视实时预览">
          <div className="preview-top"><span className="preview-label"><span className={`status-dot ${live ? 'online' : ''}`}/>{live ? '电视实况' : '电视预览'}</span><span className="preview-location">{status.model || '三楼 · Redmi X'}<span className="live-tag">LIVE</span></span></div>
          <div className="video-stage" ref={viewer} tabIndex={0} onKeyDown={keyboardRemote} aria-label="电视画面，点击后可使用方向键、Enter 和 Esc 遥控"><canvas ref={canvas} width="1920" height="1080" aria-label="小米电视实时视频" className={ready ? 'visible' : ''}/>{(!ready || error) && <div className="video-placeholder"><Icon name="cast" size={32}/><h2>{!supported ? '请用 Chrome 或 Edge 打开' : error ? '等待电视连接' : '正在连接电视'}</h2><p>{error || status.message}</p>{error && <button className="glass-pill" onClick={connect} disabled={busy}>重新连接</button>}</div>}{!active && ready && <div className="paused-label">同步已暂停</div>}<button className="stage-fullscreen" aria-label="全屏观看" onClick={fullscreen}><Icon name="expand" size={18}/></button></div>
          <div className="preview-toolbar"><span>{live ? `${stats.fps} fps` : phaseText}<span className="toolbar-dot">·</span>{live ? `${stats.mbps.toFixed(1)} Mbps` : '无线同步'}</span><div><button aria-label={active ? '暂停同步' : '恢复同步'} onClick={() => setActive(!active)}><Icon name={active ? 'pause' : 'play'} size={16}/></button><button aria-label="保存截图" onClick={screenshot} disabled={!ready}><Icon name="camera" size={17}/></button><button aria-label="重新连接" onClick={connect} disabled={busy}><Icon name="refresh" size={16}/></button></div></div>
        </section>
      </section>
      <section className="apps-section" aria-label="常用应用"><div className="apps-heading"><h2>为你常驻</h2><span>停留即打开<span className="small-divider"/>HOVER TO PLAY</span></div><div className="app-dock">{APPS.map((item, i) => <button key={item.id} className={`app-card ${item.id} ${selected === item.id ? 'selected' : ''}`} aria-label={`打开${item.name}`} aria-pressed={selected === item.id} onPointerEnter={e => { if (e.pointerType !== 'touch') hover(item.id); }} onPointerLeave={cancelHover} onFocus={() => hover(item.id)} onBlur={cancelHover} onClick={() => launch(item.id)}><div className="app-art"><AppMark id={item.id}/>{installed[item.id] === false && <span className="install-badge">待安装</span>}<span className="app-number">0{i + 1}</span></div><div className="app-caption"><span>{item.name}</span><span className="app-caption-en">{launching === item.id ? '正在打开…' : item.en}</span></div><span className="selection-line"/></button>)}</div></section>
      <footer className="desktop-footer"><span className="wallpaper-credit">HAWAIʻI<span/>AERIAL COLLECTION</span><span><Icon name="check" size={14}/>实况画面 · 本机传输</span><span className="footer-brand">GLASS<span>HOME</span></span></footer>
    </main>
    {panel && <><button className="panel-backdrop" aria-label="关闭面板" onClick={() => setPanel('')}/><aside className="floating-panel" aria-label={panel === 'remote' ? '电视遥控器' : '连接设置'}><div className="panel-title"><h2>{panel === 'remote' ? '遥控电视' : '连接你的电视'}</h2><button aria-label="关闭" className="close-button" onClick={() => setPanel('')}>×</button></div>{panel === 'remote' ? <div tabIndex={0} onKeyDown={keyboardRemote} className="remote-body"><p className="panel-subtitle">{live ? '已连接 · 三楼 Redmi X' : '等待连接'}</p><div className="remote-dpad">{[['UP','up','上'],['LEFT','left','左'],['OK',null,'确认'],['RIGHT','right','右'],['DOWN','down','下']].map(([key, icon, label]) => <button key={key} aria-label={`遥控${label}`} disabled={!live} className={`remote-${key.toLowerCase()} ${pressedKey === key ? 'key-active' : ''}`} onClick={() => remoteKey(key)}>{icon ? <Icon name={icon} size={24}/> : 'OK'}</button>)}</div><div className="remote-shortcuts">{[['BACK','back','返回'],['HOME','home','主页'],['MENU','menu','菜单']].map(([key,icon,label]) => <button key={key} onClick={() => key === 'HOME' ? home() : remoteKey(key)} disabled={!live} aria-label={`遥控${label}`} className={pressedKey === key ? 'key-active' : ''}><Icon name={icon} size={20}/><span>{label}</span></button>)}</div><div className="remote-volume"><span>音量</span><button aria-label="音量减" disabled={!live} onClick={() => remoteKey('VOLUME_DOWN')}><Icon name="minus" size={18}/></button><button aria-label="静音切换" disabled={!live} onClick={() => remoteKey('MUTE')}><Icon name="mute" size={18}/></button><button aria-label="音量加" disabled={!live} onClick={() => remoteKey('VOLUME_UP')}><Icon name="plus" size={18}/></button></div><p className="keyboard-hint">点击电视画面后，可用方向键、Enter、Esc 遥控。</p></div> : <><p className="panel-subtitle">{status.model || '小米 / Redmi 电视'} · {phaseText}</p><form onSubmit={connect}><label htmlFor="tv-ip">电视 IP 地址</label><div className="ip-input"><Icon name="wifi" size={18}/><input id="tv-ip" value={ip} onChange={e => setIp(e.target.value)} autoComplete="off" spellCheck="false"/></div><label htmlFor="quality">画面清晰度</label><select id="quality" value={status.quality || '1080'} disabled={busy} onChange={e => connect(null, e.target.value)}><option value="1080">高清 · 1080p</option><option value="720">流畅 · 720p</option></select><button className="connect-button" disabled={busy || !supported}>{busy ? '正在连接…' : '连接电视'}<Icon name="arrow" size={18}/></button></form><p className="keyboard-hint">电脑与电视保持同一 Wi-Fi，电视需允许本机 ADB 调试。</p></>}</aside></>}
    {toast && <div role="status" className="toast">{toast}</div>}
  </div>;
}
