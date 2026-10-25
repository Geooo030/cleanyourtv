import test from 'node:test';
import assert from 'node:assert/strict';
import { remoteCommand } from '../server/remote.mjs';
import { MirrorSession } from '../server/mirror.mjs';

test('web HOME opens Glass explicitly instead of injecting Xiaomi HOME', () => {
  assert.deepEqual(remoteCommand('HOME'), ['shell','am','start','-n','com.gathin.glasshome/.GlassHomeActivity']);
  assert.deepEqual(remoteCommand('BACK'), ['shell','input','keyevent','4']);
  assert.throws(() => remoteCommand('shell reboot'));
});
test('HOME dismisses system dialogs and opens Glass before the next queued key', async () => {
  const session = new MirrorSession(); session.state.phase = 'streaming';
  let release, started;
  const gate = new Promise(r => release = r), begin = new Promise(r => started = r), commands = [];
  session.device = async args => { commands.push(args); if (commands.length === 1) { started(); await gate; } return 'Starting'; };
  const first = session.key('UP'); await begin;
  const home = session.key('HOME');
  const next = session.key('RIGHT');
  release(); await Promise.all([first, home, next]);
  assert.deepEqual(commands, [
    remoteCommand('UP'),
    ['shell', 'am', 'broadcast', '-a', 'android.intent.action.CLOSE_SYSTEM_DIALOGS', '--es', 'reason', 'homekey'],
    remoteCommand('HOME'),
    remoteCommand('RIGHT'),
  ]);
});
