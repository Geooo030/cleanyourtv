import net from 'node:net';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { EventEmitter } from 'node:events';
import { randomInt } from 'node:crypto';
import { existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { VideoParser, validateTarget } from './protocol.mjs';
import { remoteCommand } from './remote.mjs';
import { protectedWindow } from './window.mjs';

const exec = promisify(execFile);
const sleep = ms => new Promise(r => setTimeout(r, ms));
let bundledAdb;
for (let directory = import.meta.dirname; ; directory = dirname(directory)) {
  const candidate = resolve(directory, '.local-tools/platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
  if (existsSync(candidate)) { bundledAdb = candidate; break; }
  if (dirname(directory) === directory) break;
}
const adb = process.env.ADB_PATH || bundledAdb || 'adb';
const serverJar = resolve(import.meta.dirname, '../vendor/scrcpy-server-v5.0.1');

export class MirrorSession extends EventEmitter {
  config = { ip: process.env.TV_HOST || '192.168.31.81', port: 5555, quality: process.env.TV_MIRROR_QUALITY || '1080' };
  state = { phase: 'idle', message: '准备就绪，连接电视即可开始同步', model: '', width: 0, height: 0, bytes: 0, frames: 0, startedAt: null, protected: false, protectedApp: '' };
  inspectingWindow = false;
  async inspectWindow() {
    if (this.inspectingWindow || this.state.phase !== 'streaming') return;
    this.inspectingWindow = true;
    const generation = this.generation;
    try {
      const state = protectedWindow(await this.device(['shell', 'dumpsys', 'window', 'windows'], 4000));
      if (generation === this.generation && (state.protected !== this.state.protected || state.protectedApp !== this.state.protectedApp)) this.update(state);
    } catch {} finally { this.inspectingWindow = false; }
  }
  generation = 0;
  starting = null;
  codecPacket = null;
  socket = null;
  process = null;
  forwardPort = null;
  remotePath = null;
  remotePid = null;
  serverLog = '';
  keyQueue = Promise.resolve();
  pendingKeys = 0;
  control(action) {
    const run = this.keyQueue.then(action);
    this.keyQueue = run.catch(() => {});
    return run;
  }
  async key(name) {
    const args = remoteCommand(name);
    if (this.state.phase !== 'streaming') throw new Error('请先连接电视，再使用遥控器');
    if (this.pendingKeys >= 8) throw new Error('按键发送过快，请稍等');
    this.pendingKeys++;
    // Injected HOME bypasses the accessibility key filter on Xiaomi firmware.
    // Open our home explicitly so the web remote matches the physical remote.
    const generation = this.generation;
    const run = this.control(async () => {
      if (generation !== this.generation) throw new Error('连接已改变，请重新操作');
      if (name === 'HOME') {
        // Xiaomi settings are a SYSTEM_DIALOG over the resumed activity. Starting
        // Glass alone leaves that dialog visible, so dismiss it as Home would.
        await this.device(['shell', 'am', 'broadcast', '-a', 'android.intent.action.CLOSE_SYSTEM_DIALOGS', '--es', 'reason', 'homekey'], 5000);
        if (generation !== this.generation) throw new Error('连接已改变，请重新操作');
      }
      return this.device(args, 5000);
    });
    try { await run; if (name === 'HOME') this.update({ protected: false, protectedApp: '' }); } finally { this.pendingKeys--; }
  }
  snapshot() { return { ...this.state, ...this.config }; }
  update(patch) { Object.assign(this.state, patch); this.emit('status', this.snapshot()); }
  async command(args, timeout = 12000) {
    try { return (await exec(adb, args, { timeout, windowsHide: true, maxBuffer: 1024 * 1024 })).stdout.trim(); }
    catch (error) { throw new Error((error.stderr || error.stdout || error.message).trim()); }
  }
  async device(args, timeout) { return this.command(['-s', validateTarget(this.config.ip, this.config.port), ...args], timeout); }
  async configure(config) {
    validateTarget(config.ip, config.port);
    if (!['720', '1080'].includes(config.quality)) throw new Error('请选择 720p 或 1080p');
    await this.stop();
    this.config = { ip: config.ip, port: Number(config.port), quality: config.quality };
    this.update({ phase: 'idle', message: '连接配置已更新', model: '', width: 0, height: 0 });
  }
  start() {
    if (this.starting) return this.starting;
    if (this.state.phase === 'streaming') return Promise.resolve();
    const id = ++this.generation;
    const check = () => { if (id !== this.generation) throw new Error('Cancelled'); };
    this.starting = (async () => {
      try {
        if (!existsSync(serverJar)) throw new Error('缺少 scrcpy-server，请先运行 npm run setup');
        this.update({ phase: 'connecting', message: '正在连接电视的 ADB…', bytes: 0, frames: 0, startedAt: null });
        const serial = validateTarget(this.config.ip, this.config.port);
        const connected = await this.command(['connect', serial]); check();
        if (/failed|cannot|refused|unable/i.test(connected)) throw new Error(connected);
        const state = await this.device(['get-state']); check();
        if (state !== 'device') throw new Error('请在电视上允许这台电脑进行 ADB 调试');
        const model = await this.device(['shell', 'getprop', 'ro.product.model']); check();
        const scid = randomInt(0, 0x7fffffff).toString(16).padStart(8, '0');
        this.remotePath = `/data/local/tmp/tv-mirror-${scid}.jar`;
        this.update({ model, message: '正在启动电视视频采集…' });
        await this.device(['push', serverJar, this.remotePath]); check();
        this.forwardPort = Number(await this.device(['forward', 'tcp:0', `localabstract:scrcpy_${scid}`])); check();
        if (!this.forwardPort) throw new Error('无法建立 ADB 视频转发');
        const max = this.config.quality === '1080' ? 1920 : 1280;
        const remoteArgs = ['app_process', '/', 'com.genymobile.scrcpy.Server', '5.0.1',
          `scid=${scid}`, 'tunnel_forward=true', 'audio=false', 'control=false', 'video_codec=h264', `max_size=${max}`,
          'max_fps=30', `video_bit_rate=${max === 1920 ? 6000000 : 3000000}`, 'send_device_meta=false',
          'send_dummy_byte=true', 'send_stream_meta=true', 'send_frame_meta=true', 'video_codec_options=i-frame-interval=1', 'power_on=false'];
        this.serverLog = '';
        const script = `echo TV_MIRROR_PID=$$; CLASSPATH=${this.remotePath} exec ${remoteArgs.join(' ')}`;
        this.process = spawn(adb, ['-s', serial, 'shell', script], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
        const log = data => {
          this.serverLog = (this.serverLog + data.toString()).slice(-4000);
          const match = this.serverLog.match(/TV_MIRROR_PID=(\d+)/);
          if (match && id === this.generation) this.remotePid = Number(match[1]);
        };
        this.process.stdout.on('data', log); this.process.stderr.on('data', log);
        this.process.on('error', error => { if (id === this.generation) this.fail(error); });
        this.process.on('exit', code => {
          if (id === this.generation && this.state.phase === 'streaming') this.fail(new Error(`电视采集已结束 (${code})，请重新连接`));
        });
        let connection;
        for (let attempt = 0; attempt < 24; attempt++) {
          check();
          try { connection = await openVideo(this.forwardPort); break; }
          catch { await sleep(250); }
        }
        check();
        if (!connection) throw new Error(this.serverLog || '电视没有提供画面，请保持电视开机并允许 ADB 调试');
        this.socket = connection.socket;
        const parser = new VideoParser();
        parser.on('session', dimensions => {
          this.codecPacket = null;
          this.update({ ...dimensions, phase: 'streaming', message: '正在实时同步', startedAt: Date.now() });
        });
        parser.on('packet', (packet, isConfig) => {
          this.state.bytes += packet.length;
          if (isConfig) this.codecPacket = packet;
          else this.state.frames++;
          this.emit('packet', packet);
        });
        const consume = chunk => { try { parser.push(chunk); } catch (error) { this.fail(error); } };
        this.socket.on('data', consume);
        this.socket.on('error', error => { if (id === this.generation) this.fail(error); });
        this.socket.on('close', () => { if (id === this.generation) this.fail(new Error('视频连接已断开，请确认电视仍在同一 Wi-Fi')); });
        consume(connection.first);
        this.socket.resume();
      } catch (error) {
        if (id !== this.generation) return;
        await this.release();
        this.update({ phase: 'error', message: friendlyError(error.message) });
      }
    })().finally(() => { if (id === this.generation) this.starting = null; });
    return this.starting;
  }
  async fail(error) {
    ++this.generation;
    this.starting = null;
    await this.release();
    this.update({ phase: 'error', message: friendlyError(error.message) });
  }
  async release() {
    this.socket?.destroy(); this.socket = null;
    const process = this.process, port = this.forwardPort, path = this.remotePath, pid = this.remotePid;
    this.process = null; this.remotePid = null;
    this.forwardPort = null; this.remotePath = null; this.codecPacket = null;
    // On Windows, killing adb.exe alone can leave the remote encoder alive.
    // Check this session's unique scid before terminating its recorded PID.
    if (pid && path) {
      const command = await this.device(['shell', 'cat', `/proc/${pid}/cmdline`], 2500).catch(() => '');
      const scid = path.match(/tv-mirror-([0-9a-f]{8})\.jar$/)?.[1];
      if (scid && command.includes('com.genymobile.scrcpy.Server') && command.includes(`scid=${scid}`))
        await this.device(['shell', 'kill', String(pid)], 2500).catch(() => {});
    }
    process?.kill();
    if (port) await this.device(['forward', '--remove', `tcp:${port}`]).catch(() => {});
    if (path) await this.device(['shell', 'rm', '-f', path]).catch(() => {});
  }
  async stop() {
    ++this.generation; this.starting = null;
    await this.release();
    this.update({ phase: 'idle', message: '同步已暂停' });
  }
}

function openVideo(port) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: '127.0.0.1', port });
    socket.setNoDelay(true);
    let done = false;
    const timer = setTimeout(() => fail(new Error('Video handshake timeout')), 1500);
    function fail(error) { if (done) return; done = true; clearTimeout(timer); socket.destroy(); reject(error); }
    socket.once('error', fail); socket.once('end', () => fail(new Error('Video socket closed')));
    socket.once('data', first => { if (done) return; done = true; clearTimeout(timer); socket.pause(); socket.removeListener('error', fail); resolve({ socket, first }); });
  });
}
function friendlyError(message) {
  if (/unauthorized/i.test(message)) return '电视正在等待授权：请在电视上选择“允许 USB 调试”';
  if (/refused|10061|unable to connect|failed to connect|timeout|timed out|offline|Command failed:.*connect/i.test(message)) return '暂时连接不上电视。请确认电视开机、IP 正确，并在“账号与安全”里开启 ADB 调试。';
  if (/ENOENT/.test(message)) return '找不到 ADB。请设置 ADB_PATH，或使用工作区自带的 platform-tools。';
  return message.slice(0, 800);
}
