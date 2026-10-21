import React, { useState } from 'react';
import { Icon } from './Icons';

export function Monitor({ status, ip, setIp, active, setActive, ready, error, busy, stats, toast, pressedKey, canvas, viewer, supported, live, phaseText, connect, screenshot, fullscreen, remoteKey, keyboardRemote }) {
  const [settings, setSettings] = useState(false);
  return <main className="monitor">
    <section className="screen-panel" aria-label="电视实时画面">
      <header className="screen-heading"><span><Icon name="screen" size={18}/>Redmi X <span className="heading-detail">三楼电视</span></span><span className="connection"><i className={live ? 'online' : ''}/>{phaseText}</span></header>
      <div className="video-stage" ref={viewer} tabIndex={0} onKeyDown={keyboardRemote} aria-label="电视画面，点击后可使用方向键、Enter 和 Esc 遥控">
        <canvas ref={canvas} width="1920" height="1080" aria-label="小米电视实时视频" className={ready ? 'visible' : ''}/>
        {(!ready || error) && <div className="video-placeholder"><Icon name="cast" size={35}/><h1>{!supported ? '请用 Chrome 或 Edge 打开' : error ? '等待电视连接' : '正在连接电视'}</h1><p>{error || status.message}</p>{error && <button className="primary" onClick={connect} disabled={busy}>重新连接</button>}</div>}
        {!active && ready && <span className="paused-label">同步已暂停</span>}
        {ready && status.protected && <div className="video-placeholder protected-message"><Icon name="screen" size={35}/><h1>{status.protectedApp || '当前应用'}已在电视打开</h1><p>此应用限制屏幕同步，电脑无法显示其页面。<br/>遥控器仍可使用，按主页返回 Glass。</p><button className="primary" onClick={() => remoteKey('HOME')}>返回 Glass 主页</button></div>}
      </div>
      <div className="screen-toolbar"><div><button aria-label={active ? '暂停同步' : '恢复同步'} title={active ? '暂停同步' : '恢复同步'} onClick={() => setActive(!active)}><Icon name={active ? 'pause' : 'play'} size={17}/></button><button aria-label="保存截图" title="保存截图" onClick={screenshot} disabled={!ready}><Icon name="camera" size={18}/></button><button aria-label="全屏观看" title="全屏观看" onClick={fullscreen}><Icon name="expand" size={17}/></button></div><span>{live ? `${status.width} × ${status.height} · ${stats.fps} fps` : '电视实况'}<span className="stream-rate">{live ? ` · ${stats.mbps.toFixed(1)} Mbps` : ''}</span></span><button aria-label="连接设置" title="连接设置" aria-expanded={settings} onClick={() => setSettings(!settings)}><Icon name="wifi" size={17}/></button></div>
      {settings && <form className="connection-settings" onSubmit={connect}><label htmlFor="tv-ip">电视 IP<input id="tv-ip" value={ip} onChange={e => setIp(e.target.value)} spellCheck="false" autoComplete="off"/></label><label htmlFor="quality">清晰度<select id="quality" value={status.quality || '1080'} disabled={busy} onChange={e => connect(null, e.target.value)}><option value="1080">1080p</option><option value="720">720p</option></select></label><button className="primary" disabled={busy || !supported}>{busy ? '连接中…' : '重新连接'}</button></form>}
    </section>
    <aside className="remote-panel" aria-label="电视遥控器" tabIndex={0} onKeyDown={keyboardRemote}>
      <header><h2>遥控器</h2><span>无线控制</span></header>
      <div className="remote-dpad">{[['UP','up','上'],['LEFT','left','左'],['OK',null,'确认'],['RIGHT','right','右'],['DOWN','down','下']].map(([key, icon, label]) => <button key={key} aria-label={`遥控${label}`} disabled={!live} className={`remote-${key.toLowerCase()} ${pressedKey === key ? 'key-active' : ''}`} onClick={() => remoteKey(key)}>{icon ? <Icon name={icon} size={25}/> : 'OK'}</button>)}</div>
      <div className="remote-shortcuts">{[['BACK','back','返回'],['HOME','home','主页'],['MENU','menu','菜单']].map(([key,icon,label]) => <button key={key} onClick={() => remoteKey(key)} disabled={!live} aria-label={`遥控${label}`} className={pressedKey === key ? 'key-active' : ''}><Icon name={icon} size={21}/><span>{label}</span></button>)}</div>
      <div className="remote-volume"><span>音量</span><div><button aria-label="音量减" disabled={!live} onClick={() => remoteKey('VOLUME_DOWN')}><Icon name="minus" size={19}/></button><button aria-label="静音切换" disabled={!live} onClick={() => remoteKey('MUTE')}><Icon name="mute" size={19}/></button><button aria-label="音量加" disabled={!live} onClick={() => remoteKey('VOLUME_UP')}><Icon name="plus" size={19}/></button></div></div>
      <p className="keyboard-hint">点击画面后，用方向键、Enter 和 Esc 遥控。主页键返回 Glass。</p>
    </aside>
    {toast && <div role="status" className="toast">{toast}</div>}
  </main>;
}
