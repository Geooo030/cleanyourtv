import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';
import { randomBytes } from 'node:crypto';
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { Readable } from 'node:stream';
import { authorized, cdnUrl, pageOffset } from './security.mjs';
import { MusicLibrary } from './library.mjs';
import { rhythm } from './rhythm.mjs';
import { HomePreviews } from './home-previews.mjs';

const dir = path.dirname(fileURLToPath(import.meta.url));
const runtime = path.join(dir, '.runtime');
fs.mkdirSync(runtime, { recursive: true });
const configPath = path.join(runtime, 'config.json');
if (!fs.existsSync(configPath)) fs.writeFileSync(configPath, JSON.stringify({ port: 5176, token: randomBytes(32).toString('hex') }));
const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
process.env.TEMP = process.env.TMP = runtime;
process.env.ENABLE_GENERAL_UNBLOCK = process.env.ENABLE_RANDOM_CN_IP = process.env.ENABLE_PROXY = 'false';
delete process.env.NETEASE_COOKIE;
fs.writeFileSync(path.join(runtime, 'anonymous_token'), '');
// The dependency can log request cookies on errors. Only our sanitized events reach logs.
console.log = console.info = console.warn = console.error = console.debug = () => {};
const log = message => process.stdout.write(new Date().toISOString() + ' ' + message + '\n');
process.on('uncaughtExceptionMonitor',error=>log('Unexpected failure: '+error.name));
process.on('unhandledRejection',()=>{log('Unexpected asynchronous failure');process.exit(1);});
const require = createRequire(import.meta.url);
const apiRoot = path.dirname(require.resolve('@neteasecloudmusicapienhanced/api'));
const { cookieToJson } = require(path.join(apiRoot, 'util/index.js'));
const request = require(path.join(apiRoot, 'util/request.js'));
let cookie = '', key = '', generation = 0, polling = false, ids = [], uid = 0;
let likesUpdatedAt=0,likesPending;
const tracks = new Map(), media = new Map(), pages = new Map();
const state = { phase: 'idle', message: '登录网易云音乐', nickname: '', count: 0, qrImage: null };
const library = new MusicLibrary(api,tracks,()=>uid);
const homePreviews=new HomePreviews({directory:runtime});
const rhythms=new Map();let analysis=Promise.resolve();
const setState = (phase, message) => Object.assign(state, { phase, message });
function vault(mode, input = '') {
  return new Promise((resolve, reject) => {
    const child = spawn('powershell.exe', ['-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',path.join(dir,'session.ps1'),mode,path.join(runtime,'session.dpapi')], { windowsHide: true, stdio: ['pipe','pipe','pipe'] });
    let output = ''; child.stdout.setEncoding('utf8'); child.stdout.on('data', s => output += s);
    child.stderr.resume(); child.on('error', reject);
    child.on('exit', code => code === 0 ? resolve(output) : reject(Error('SESSION_STORAGE')));
    child.stdin.on('error', () => {}); child.stdin.end(input);
  });
}
async function api(name, args = {}) {
  const result = await require(path.join(apiRoot,'module',name+'.js'))({ cookie: cookieToJson(cookie), timeout: 15000, timestamp: Date.now(), ...args }, request);
  if (result.body?.code === 301) { cookie = ''; state.qrImage = null; setState('idle','登录已过期，请重新扫码'); throw Error('LOGIN_EXPIRED'); }
  return result.body;
}
async function loadLibrary() {
  setState('loading','正在同步喜欢的音乐…');
  const profile = (await api('login_status'))?.data?.profile;
  if (!profile?.userId) { cookie=''; setState('idle','请重新扫码登录'); return; }
  uid = profile.userId; state.nickname = profile.nickname || '我的音乐';
  await refreshLikes(true);tracks.clear();media.clear();
  state.count = ids.length; state.qrImage = null;
  setState('ready', '喜欢的音乐'); log('Library ready: ' + ids.length + ' tracks');
}
async function refreshLikes(force=false){
  if(!force&&Date.now()-likesUpdatedAt<60000)return;
  if(likesPending?.generation===generation)return likesPending.promise;
  const job={generation,promise:null};likesPending=job;
  job.promise=(async()=>{const result=await api('likelist',{uid});if(!Array.isArray(result.ids))throw Error('LIBRARY_UNAVAILABLE');const ordered=await library.likedOrder(result.ids);if(job.generation!==generation)return;ids=ordered;pages.clear();likesUpdatedAt=Date.now();state.count=ids.length;})().finally(()=>{if(likesPending===job)likesPending=null;});
  return job.promise;
}
async function startQr() {
  if (cookie && state.phase === 'ready') return;
  const current = ++generation; state.qrImage = null; setState('starting','正在获取二维码…');
  try {
    const fresh = (await api('login_qr_key'))?.data?.unikey;
    const qr = await api('login_qr_create', { key: fresh, qrimg: true, platform: 'web' });
    if (generation !== current) return;
    if (!fresh || !qr?.data?.qrimg) throw Error('QR_UNAVAILABLE');
    key = fresh; state.qrImage = qr.data.qrimg;
    setState('waiting','用网易云音乐扫码登录');
  } catch { if (generation === current) setState('error','暂时无法获取二维码，请重试'); }
}
async function pollLogin() {
  if (polling || !key || !['waiting','scanned'].includes(state.phase)) return;
  polling = true; const current = generation;
  try {
    const result = await api('login_qr_check', { key });
    if (current !== generation) return;
    if (result.code === 803) {
      if (!result.cookie?.includes('MUSIC_U=')) throw Error('LOGIN_FAILED');
      cookie = result.cookie; key = ''; state.qrImage = null;
      await vault('save',cookie); await loadLibrary(); log('Login saved using Windows DPAPI');
    } else if (result.code === 802) setState('scanned','已扫码，请在手机上确认');
    else if (result.code === 800) setState('expired','二维码已过期');
  } catch { if (cookie) setState('error','音乐库同步失败，请重试'); }
  finally { polling = false; }
}
async function libraryPage(offset) {
  if (state.phase !== 'ready') throw Error('LOGIN_REQUIRED');
  if(offset===0)await refreshLikes();
  if (pages.has(offset)) return pages.get(offset);
  const batch = ids.slice(offset,offset + 40);
  const result = batch.length ? await api('song_detail', { ids: batch.join(',') }) : { songs: [] };
  if (!Array.isArray(result.songs)) throw Error('LIBRARY_UNAVAILABLE');
  const byId = new Map(result.songs.map(s => [s.id,s]));
  const list = batch.map(id => byId.get(id)).filter(Boolean).map(s => ({ id: s.id, name: s.name, artist: (s.ar || []).map(a => a.name).join(' / '), album: s.al?.name || '', cover: s.al?.picUrl || '', duration: s.dt || 0 }));
  list.forEach(t => tracks.set(t.id,t));
  const page = { tracks: list, total: ids.length, next: offset + batch.length, hasMore: offset + batch.length < ids.length };
  pages.set(offset,page); return page;
}
async function getTrack(id) {
  if (!tracks.has(id)) {
    if(!Number.isSafeInteger(id)||id<=0)throw Error('UNKNOWN_TRACK');
    const details=await api('song_detail',{ids:String(id)});const s=details.songs?.[0];
    if(!s)throw Error('UNKNOWN_TRACK');
    tracks.set(id,{id:s.id,name:s.name,artist:(s.ar||[]).map(a=>a.name).join(' / '),album:s.al?.name||'',cover:s.al?.picUrl||'',duration:s.dt||0});
  }
  return tracks.get(id);
}
async function select(id) {
  await getTrack(id);
  // xeapi currently fails public-key negotiation; the verified eapi transport works.
  const result = await api('song_url_v1', { id: String(id), level: 'standard', crypto: 'eapi', unblock: 'false' });
  const item = result?.data?.[0];
  if (!item?.url) throw Error('NO_PLAYABLE_AUDIO');
  if (item.freeTrialInfo != null && item.freeTrialInfo !== 'null') throw Error('TRIAL_ONLY');
  const url = cdnUrl(item.url).toString(); media.set(id, { url, expires: Date.now() + 15*60*1000 });
  return { track: tracks.get(id), audio: '/audio/' + id };
}
async function remote(url, range, signal) {
  let current = cdnUrl(url);
  for (let hop=0;hop<4;hop++) {
    const response = await fetch(current, { headers: range ? { Range: range } : {}, signal, redirect:'manual' });
    if ([301,302,303,307,308].includes(response.status)) {
      const next = response.headers.get('location'); await response.body?.cancel(); current = cdnUrl(new URL(next,current).href); continue;
    }
    return response;
  }
  throw Error('CDN_REDIRECT');
}
function json(res, value, code=200) { res.writeHead(code, {'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'}); res.end(JSON.stringify(value)); }
const messages = { LOGIN_REQUIRED:'请先扫码登录', LOGIN_EXPIRED:'登录已过期，请重新扫码', NO_PLAYABLE_AUDIO:'这首歌暂时无法播放', TRIAL_ONLY:'这首歌仅提供试听，需要相应会员或专辑权限', UNKNOWN_TRACK:'请刷新喜欢列表后重试' };
const server = http.createServer(async (req,res) => {
  const abort = new AbortController(); res.on('close',()=>abort.abort());
  try {
    if(/^\/audio\/\d+/.test(req.url))log('Audio request: '+req.method+' paired='+authorized(req.headers.authorization,config.token));
    // Native clients use a per-install token. Browser origins are never accepted.
    if (req.headers.origin || !authorized(req.headers.authorization,config.token)) return json(res,{error:'UNAUTHORIZED'},401);
    const url = new URL(req.url,'http://localhost');
    const preview=url.pathname.match(/^\/api\/home-preview\/(bilibili|aurora)$/),previewCover=url.pathname.match(/^\/home-preview-cover\/(bilibili|aurora)\/([a-zA-Z0-9]+)$/);
    if(req.method==='GET'&&preview)return json(res,await homePreviews.feed(preview[1]));
    if(req.method==='GET'&&previewCover){const result=await homePreviews.cover(previewCover[1],previewCover[2]);res.writeHead(200,{'Content-Type':result.type,'Content-Length':result.body.length,'Cache-Control':'private, max-age=1800'});return res.end(result.body);}
    if (req.method==='GET' && url.pathname==='/api/status') return json(res,state);
    if (req.method==='POST' && url.pathname==='/api/login') {
      if (cookie) { await loadLibrary(); } else await startQr();
      return json(res,state);
    }
    if (req.method==='POST' && url.pathname==='/api/logout') {
      ++generation; key=''; cookie=''; ids=[]; likesUpdatedAt=0; tracks.clear(); pages.clear(); media.clear();library.clear();rhythms.clear();
      Object.assign(state,{nickname:'',count:0,qrImage:null}); fs.rmSync(path.join(runtime,'session.dpapi'),{force:true}); setState('idle','登录网易云音乐'); return json(res,state);
    }
    if (req.method==='GET' && url.pathname==='/api/likes') return json(res,await libraryPage(pageOffset(url.searchParams.get('offset'))));
    if(req.method==='GET'&&url.pathname==='/api/collections')return json(res,await library.collections(pageOffset(url.searchParams.get('offset')),url.searchParams.get('kind')||'all'));
    if(req.method==='GET'&&url.pathname==='/api/search')return json(res,await library.search(url.searchParams.get('q')||'',url.searchParams.get('type')||'song',pageOffset(url.searchParams.get('offset'))));
    const playlist=url.pathname.match(/^\/api\/playlist\/(\d+)$/),lyric=url.pathname.match(/^\/api\/lyrics\/(\d+)$/),energy=url.pathname.match(/^\/api\/rhythm\/(\d+)$/);
    if(req.method==='GET'&&playlist)return json(res,await library.playlistPage(Number(playlist[1]),pageOffset(url.searchParams.get('offset'))));
    if(req.method==='GET'&&lyric)return json(res,await library.lyric(Number(lyric[1])));
    if(req.method==='GET'&&energy){
      const id=Number(energy[1]);
      if(!rhythms.has(id)){
        const job=analysis.catch(()=>{}).then(async()=>{if(!media.has(id))await select(id);const response=await remote(media.get(id).url,null,AbortSignal.timeout(20000));if(!response.ok)throw Error('ANALYSIS_FAILED');return rhythm(response);});
        analysis=job;rhythms.set(id,job);job.catch(()=>rhythms.delete(id));
        if(rhythms.size>12)rhythms.delete(rhythms.keys().next().value);
      }
      return json(res,await rhythms.get(id));
    }
    const play=url.pathname.match(/^\/api\/play\/(\d+)$/);
    if (req.method==='POST' && play) return json(res,await select(Number(play[1])));
    const audio=url.pathname.match(/^\/audio\/(\d+)$/), cover=url.pathname.match(/^\/cover\/(\d+)$/),playlistCover=url.pathname.match(/^\/playlist-cover\/(\d+)$/);
    if (req.method==='GET' && (audio||cover||playlistCover)) {
      const id=Number((audio||cover||playlistCover)[1]); let source;
      if (audio) { if (!media.has(id)||media.get(id).expires<Date.now()) await select(id); source=media.get(id).url; }
      else { const item=playlistCover?(library.playlists.get(id)||(await library.detail(id)).playlist):await getTrack(id); if (!item?.cover) return json(res,{error:'NO_COVER'},404); const u=cdnUrl(item.cover);u.searchParams.set('param','512y512');source=u.toString(); }
      const response=await remote(source,req.headers.range,abort.signal);
      if(audio)log('Audio CDN status: '+response.status);
      if (![200,206].includes(response.status)) throw Error('CDN_UNAVAILABLE');
      const headers={'Cache-Control':'private, no-store'};
      for(const k of ['content-type','content-length','content-range','accept-ranges'])if(response.headers.has(k))headers[k]=response.headers.get(k);
      res.writeHead(response.status,headers);
      const stream=Readable.fromWeb(response.body); stream.on('error',()=>res.destroy());stream.pipe(res);return;
    }
    return json(res,{error:'NOT_FOUND'},404);
  } catch(error) {
    if(abort.signal.aborted)return;
    if(res.headersSent)return res.destroy();
    // Never return upstream errors or signed URLs: they may contain account cookies.
    return json(res,{error:messages[error.message]||'音乐服务暂时不可用，请重试'},400);
  }
});
server.requestTimeout=20000; server.headersTimeout=10000;
server.on('error',error=>{log('Service failed: '+error.code);process.exit(1);});
server.listen(config.port,'0.0.0.0',()=>log('Glass music listening on port '+config.port));
setInterval(pollLogin,2200).unref();
try { cookie=await vault('load'); if(cookie)await loadLibrary(); } catch { setState('error','连接音乐库失败，请重试'); }
function shutdown(){server.close();process.exit(0);}process.on('SIGINT',shutdown);process.on('SIGTERM',shutdown);
