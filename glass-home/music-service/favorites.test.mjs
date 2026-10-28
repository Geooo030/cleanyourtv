import test from 'node:test';
import assert from 'node:assert/strict';
import {FavoriteActions} from './favorites.mjs';
test('favorite changes are serialized and use explicit upstream boolean strings',async()=>{
  const calls=[],updates=[];
  const actions=new FavoriteActions(async(name,args)=>{calls.push([name,args]);await new Promise(r=>setTimeout(r,5));return {code:200};},()=>1,(id,liked)=>updates.push([id,liked]));
  await Promise.all([actions.set(9,true),actions.set(9,false)]);
  assert.deepEqual(calls,[['like',{id:9,like:'true'}],['like',{id:9,like:'false'}]]);
  assert.deepEqual(updates,[[9,true],[9,false]]);
});
test('failure preserves the library and does not poison subsequent mutations',async()=>{
  let succeed=false;const updates=[];
  const actions=new FavoriteActions(async()=>({code:succeed?200:500}),()=>1,(...args)=>updates.push(args));
  await assert.rejects(actions.set(9,true),/FAVORITE_FAILED/);assert.equal(updates.length,0);
  succeed=true;await actions.set(9,true);assert.deepEqual(updates,[[9,true]]);
  await assert.rejects(actions.set(0,true),/INVALID_FAVORITE/);await assert.rejects(actions.set(9,'false'),/INVALID_FAVORITE/);
});
test('a response from a previous account cannot alter the next account library',async()=>{
  let epoch=1,finish;const updates=[];
  const actions=new FavoriteActions(()=>new Promise(r=>finish=r),()=>epoch,(...args)=>updates.push(args));
  const active=actions.set(9,true),queued=actions.set(8,true);await new Promise(r=>setImmediate(r));
  epoch++;finish({code:200});await assert.rejects(active,/LOGIN_REQUIRED/);await assert.rejects(queued,/LOGIN_REQUIRED/);assert.equal(updates.length,0);
});
