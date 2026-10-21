import test from 'node:test';
import assert from 'node:assert/strict';
import { protectedWindow } from '../server/window.mjs';
const sample = (display, visible, flags) => `\n  Window #3 Window{test}:\n mDisplayId=${display} rootTaskId=43\n package=com.xiaodianshi.tv.yst appop=NONE\n mAttrs={\n fl=FULLSCREEN ${flags} HARDWARE_ACCELERATED\n }\n isOnScreen=${visible}\n isVisible=${visible}\n`;
test('reports visible secure windows on the physical TV display', () => {
  assert.deepEqual(protectedWindow(sample(0,true,'SECURE')), { protected:true, protectedApp:'云视听小电视' });
});
test('ignores hidden windows, secondary displays and nonsecure flags', () => {
  for (const dump of [sample(0,false,'SECURE'),sample(9,true,'SECURE'),sample(0,true,'ALT_FOCUSABLE_IM'),sample(0,true,'NOT_SECURE')])
    assert.deepEqual(protectedWindow(dump), {protected:false,protectedApp:''});
});
