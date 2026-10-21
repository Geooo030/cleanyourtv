import test from 'node:test';
import assert from 'node:assert/strict';
import { VideoParser, CONFIG_FLAG, KEY_FLAG, validateTarget } from '../server/protocol.mjs';
import { resolveRemoteKey } from '../server/remote.mjs';
import { codecFromAnnexB } from '../src/decoder.js';

function packet(flags, body) {
  const header = Buffer.alloc(12); header.writeBigUInt64BE(flags); header.writeUInt32BE(body.length, 8);
  return Buffer.concat([header, body]);
}
function handshake(width = 1920, height = 1080) {
  const dimensions = Buffer.alloc(12); dimensions.writeUInt32BE(0x80000000); dimensions.writeUInt32BE(width, 4); dimensions.writeUInt32BE(height, 8);
  return Buffer.concat([Buffer.from([0]), Buffer.from('h264'), dimensions]);
}
test('parses byte-fragmented handshake and frames without losing packet boundaries', () => {
  const parser = new VideoParser(), sessions = [], packets = [];
  parser.on('session', s => sessions.push(s)); parser.on('packet', (p, config) => packets.push({ p, config }));
  const config = packet(CONFIG_FLAG, Buffer.from([0,0,0,1,0x67,0x64,0,0x28]));
  const frame = packet(KEY_FLAG | 23000n, Buffer.from([0,0,1,0x65,0x42,0x01]));
  for (const byte of Buffer.concat([handshake(), config, frame])) parser.push(Buffer.from([byte]));
  assert.deepEqual(sessions, [{ width:1920, height:1080 }]);
  assert.deepEqual(packets, [{ p:config, config:true }, { p:frame, config:false }]);
});
test('supports a resolution change inside an existing stream', () => {
  const parser = new VideoParser(), dimensions = [];
  parser.on('session', s => dimensions.push(s)); parser.push(handshake()); parser.push(handshake(1280,720).subarray(5));
  assert.deepEqual(dimensions, [{width:1920,height:1080},{width:1280,height:720}]);
});
test('rejects oversized video packets before buffering their contents', () => {
  const parser = new VideoParser(); parser.push(handshake());
  const huge = Buffer.alloc(12); huge.writeUInt32BE(9*1024*1024,8);
  assert.throws(() => parser.push(huge), /8 MiB/);
});
test('reads the actual H.264 profile from both Annex-B start code lengths', () => {
  assert.equal(codecFromAnnexB(Uint8Array.from([0,0,0,1,0x67,0x64,0,0x28,0x99])), 'avc1.640028');
  assert.equal(codecFromAnnexB(Uint8Array.from([0,0,1,0x67,0x42,0xe0,0x1f,0x99,0x99])), 'avc1.42e01f');
});
test('accepts LAN targets and rejects invalid addresses and command text', () => {
  assert.equal(validateTarget('192.168.31.81',5555),'192.168.31.81:5555');
  for (const ip of ['8.8.8.8','192.168.31.999','192.168.1.1; reboot','localhost']) assert.throws(() => validateTarget(ip));
  assert.throws(() => validateTarget('192.168.1.1',0));
});
test('remote input only accepts explicitly supported key names', () => {
  assert.equal(resolveRemoteKey('HOME'),3); assert.equal(resolveRemoteKey('OK'),23);
  for (const key of ['reboot','3; reboot','__proto__',null,26]) assert.throws(() => resolveRemoteKey(key));
});
