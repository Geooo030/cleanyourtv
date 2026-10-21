import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { LiveDecoder } from './decoder';
import './styles.css';
import { Desktop } from './Desktop';


function App() {
  const [status, setStatus] = useState({ phase: 'connecting', message: '正在准备连接…', ip: '192.168.31.81', quality: '1080' });
  const [ip, setIp] = useState(localStorage.getItem('tv-mirror-ip') || '192.168.31.81');
  const [active, setActive] = useState(true), [epoch, setEpoch] = useState(0);
  const [ready, setReady] = useState(false), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const [stats, setStats] = useState({ fps: 0, decode: 0, mbps: 0 }), [toast, setToast] = useState('');
  const [pressedKey, setPressedKey] = useState('');
  const keyFeedbackTimer = useRef(null);
  const canvas = useRef(null), viewer = useRef(null), counts = useRef({ frames: 0, decode: 0, bytes: 0 });
  const toastTimer = useRef(null);
  const supported = 'VideoDecoder' in window;
  useEffect(() => {
    fetch('/api/status').then(r => r.json()).then(s => { setStatus(s); if (!localStorage.getItem('tv-mirror-ip')) setIp(s.ip); }).catch(() => setError('本地同步服务没有启动'));
    const timer = setInterval(() => {
      const c = counts.current; setStats({ fps: c.frames, decode: c.frames ? c.decode / c.frames : 0, mbps: c.bytes * 8 / 1e6 });
      counts.current = { frames: 0, decode: 0, bytes: 0 };
    }, 1000);
    return () => { clearInterval(timer); clearTimeout(toastTimer.current); clearTimeout(keyFeedbackTimer.current); };
  }, []);
  useEffect(() => {
    if (!active || !supported) return;
    let disposed = false, retry;
    const decoder = new LiveDecoder(canvas.current, elapsed => {
      if (disposed) return;
      counts.current.frames++; counts.current.decode += elapsed; setReady(true); setError('');
    }, message => { if (!disposed) setError(message); });
    const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/stream`);
    socket.binaryType = 'arraybuffer';
    socket.onmessage = event => {
      if (disposed) return;
      if (typeof event.data === 'string') { const s = JSON.parse(event.data); setStatus(s); if (s.phase === 'error') setError(s.message); }
      else { counts.current.bytes += event.data.byteLength; try { decoder.packet(event.data); } catch (e) { setError(e.message); } }
    };
    socket.onclose = () => {
      if (disposed) return;
      setReady(false); setError('视频连接已断开，正在重新连接…');
      retry = setTimeout(() => setEpoch(e => e + 1), 2500);
    };
    socket.onerror = () => { if (!disposed) setError('无法连接本机同步服务'); };
    return () => { disposed = true; clearTimeout(retry); socket.close(); decoder.close(); };
  }, [active, epoch, supported]);

  async function connect(event, quality = status.quality || '1080') {
    event?.preventDefault(); setBusy(true); setError(''); setReady(false);
    try {
      const response = await fetch('/api/config', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ip: ip.trim(), port: 5555, quality }) });
      const result = await response.json(); if (!response.ok) throw new Error(result.error);
      setStatus(result); localStorage.setItem('tv-mirror-ip', ip.trim()); setActive(true); setEpoch(e => e + 1);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  function notify(message) { setToast(message); clearTimeout(toastTimer.current); toastTimer.current = setTimeout(() => setToast(''), 2600); }
  function screenshot() {
    canvas.current.toBlob(blob => {
      if (!blob) return;
      const url = URL.createObjectURL(blob), link = document.createElement('a');
      link.href = url; link.download = `tv-screen-${new Date().toISOString().replaceAll(':', '-')}.png`; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000); notify('当前画面已保存');
    });
  }
  async function fullscreen() { try { if (document.fullscreenElement) await document.exitFullscreen(); else await viewer.current.requestFullscreen(); } catch { notify('浏览器未允许全屏显示'); } }
  async function remoteKey(key) {
    if (status.phase !== 'streaming' || !active) return;
    setPressedKey(key); clearTimeout(keyFeedbackTimer.current); keyFeedbackTimer.current = setTimeout(() => setPressedKey(''), 180);
    try {
      const r = await fetch('/api/key', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ key }) });
      const data = await r.json(); if (!r.ok) throw new Error(data.error);
    } catch (e) { notify(e.message); }
  }
  function keyboardRemote(event) {
    if (event.altKey || event.ctrlKey || event.metaKey || event.target.closest('input,select,textarea')) return;
    const mapping = { ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT', Enter: 'OK', Escape: 'BACK', Home: 'HOME', Backspace: 'BACK' };
    const key = mapping[event.key];
    if (!key || event.target.closest('button')) return;
    event.preventDefault(); if (!event.repeat) remoteKey(key);
  }
  const live = active && ready && status.phase === 'streaming' && !error;
  const phaseText = live ? '正在同步' : !active ? '已暂停' : error ? '等待连接' : '连接中';

  return <Desktop {...{ status, ip, setIp, active, setActive, ready, error, busy, stats, toast, pressedKey, canvas, viewer, supported, live, phaseText, connect, screenshot, fullscreen, remoteKey, keyboardRemote, notify }} />;
}
createRoot(document.getElementById('root')).render(<App/>);
