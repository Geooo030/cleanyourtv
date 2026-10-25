import test from 'node:test';
import assert from 'node:assert/strict';
import { authorized, cdnUrl, pageOffset } from './security.mjs';
test('pairing token is required exactly',()=>{assert(authorized('Bearer abc','abc'));for(const s of ['', 'abc', 'Bearer abcd','bearer abc'])assert(!authorized(s,'abc'));});
test('only official music CDN hosts can be proxied',()=>{
 assert.equal(cdnUrl('http://m8.music.126.net/a.mp3').protocol,'https:');
 for(const u of ['http://127.0.0.1/a','https://music.126.net.evil.test/a','https://evil-music.126.net/a','file:///etc/passwd','https://a@music.126.net/a','https://music.126.net:999/a'])assert.throws(()=>cdnUrl(u));
});
test('pagination rejects invalid offsets',()=>{assert.equal(pageOffset('40'),40);for(const o of ['NaN','-1','1.5','Infinity','100001'])assert.throws(()=>pageOffset(o));});
