import test from 'node:test';
import assert from 'node:assert/strict';
import { remoteCommand } from '../server/remote.mjs';
import { MirrorSession } from '../server/mirror.mjs';

test('web HOME opens Glass explicitly instead of injecting Xiaomi HOME', () => {
  assert.deepEqual(remoteCommand('HOME'), ['shell','am','start','-n','com.gathin.glasshome/.GlassHomeActivity']);
  assert.deepEqual(remoteCommand('BACK'), ['shell','input','keyevent','4']);
  assert.throws(() => remoteCommand('shell reboot'));
});
test('remote commands stay ordered when HOME follows a direction press', async () => {
  const session = new MirrorSession(); session.state.phase = 'streaming';
  let release, started;
  const gate = new Promise(r => release = r), begin = new Promise(r => started = r), commands = [];
  session.device = async args => { commands.push(args); if (commands.length === 1) { started(); await gate; } return 'Starting'; };
  const first = session.key('UP'); await begin;
  const home = session.key('HOME');
  release(); await Promise.all([first, home]);
  assert.deepEqual(commands, [remoteCommand('UP'), remoteCommand('HOME')]);
});
