import test from 'node:test';
import assert from 'node:assert/strict';
import { MusicLibrary } from './library.mjs';

test('likes follow collection timestamps instead of likelist order or song IDs',async()=>{
  const library=new MusicLibrary(async name=>name==='user_playlist'?{playlist:[{id:7,specialType:5}]}:{playlist:{trackIds:[{id:900,at:10},{id:2,at:30},{id:100,at:20},{id:44,at:30},{id:8,at:40},{id:2,at:30}]}},new Map(),()=>99);
  assert.deepEqual(await library.likedOrder([900,100,44,2,50]),[2,44,100,900,50]);
});

test('unpaged upstream collections are bounded and the likes list is not duplicated',async()=>{
  let calls=0;
  const library=new MusicLibrary(async()=>{calls++;return {more:false,playlist:[{id:1,specialType:5},...Array.from({length:65},(_,i)=>({id:i+2,name:`List ${i}`,trackCount:10}))]};},new Map(),()=>99);
  const first=await library.collections(0),second=await library.collections(first.next),last=await library.collections(second.next);
  assert.equal(first.items.length,30);assert.equal(first.items[0].id,2);assert.equal(first.hasMore,true);
  assert.equal(second.items.length,30);assert.equal(last.items.length,5);assert.equal(last.hasMore,false);assert.equal(calls,1);
  library.clear();await library.collections(0);assert.equal(calls,2);
});

test('created and saved playlists are classified by creator before pagination',async()=>{
  const calls=[];const pages=[{more:true,playlist:[{id:1,specialType:5,creator:{userId:99}},...Array.from({length:32},(_,i)=>({id:10+i,creator:{userId:99},subscribed:true}))]},{more:false,playlist:[...Array.from({length:35},(_,i)=>({id:100+i,creator:{userId:88},subscribed:true})),{id:200,creator:{userId:99}}]}];
  const library=new MusicLibrary(async(name,args)=>{calls.push(args.offset);return pages[calls.length-1];},new Map(),()=>99);
  const [created,saved]=await Promise.all([library.collections(0,'created'),library.collections(0,'saved')]);
  assert.deepEqual(calls,[0,33]);assert.equal(created.total,33);assert.equal(saved.total,35);assert.equal(saved.items[0].id,100);assert.ok(created.items.every(p=>p.owned));assert.ok(saved.items.every(p=>!p.owned));
  assert.deepEqual((await library.collections(30,'created')).items.map(p=>p.id),[40,41,200]);assert.equal((await library.collections(30,'saved')).items.length,5);
  await assert.rejects(()=>library.collections(0,'unknown'),/INVALID_COLLECTION_KIND/);
});

test('playlist pagination preserves playlist order even when song detail order differs',async()=>{
  const tracks=new Map();
  const library=new MusicLibrary(async(name,args)=>name==='playlist_detail'?{playlist:{id:6,name:'Library',trackIds:Array.from({length:43},(_,i)=>({id:i+1}))}}:{songs:args.ids.split(',').reverse().map(id=>({id:Number(id),name:`Song ${id}`,ar:[{name:'Artist'}],al:{name:'Album'},dt:123000}))},tracks,()=>99);
  const first=await library.playlistPage(6,0),last=await library.playlistPage(6,first.next);
  assert.equal(first.tracks.length,40);assert.equal(first.tracks[0].id,1);assert.equal(first.tracks[39].id,40);assert.equal(first.hasMore,true);
  assert.deepEqual(last.tracks.map(t=>t.id),[41,42,43]);assert.equal(last.hasMore,false);assert.equal(tracks.get(43).artist,'Artist');
});

test('song and playlist search use independent upstream types and normalize their results',async()=>{
  const calls=[],tracks=new Map();
  const library=new MusicLibrary(async(name,args)=>{calls.push(args);return args.type===1?{result:{songCount:31,songs:[{id:8,name:'Song',artists:[{name:'Singer'}],album:{name:'Album',picUrl:'https://example.test/cover'},duration:5000}]}}:{result:{playlistCount:1,playlists:[{id:9,name:'Collection',coverImgUrl:'cover',trackCount:7,creator:{nickname:'Owner'}}]}};},tracks,()=>99);
  const songs=await library.search(' hello ','song',0),lists=await library.search('hello','playlist',0);
  assert.equal(calls[0].keywords,'hello');assert.deepEqual(calls.map(c=>c.type),[1,1000]);assert.equal(songs.tracks[0].artist,'Singer');assert.equal(songs.hasMore,true);assert.equal(lists.items[0].count,7);assert.equal(lists.hasMore,false);
  await assert.rejects(()=>library.search('','song',0),/INVALID_SEARCH/);
});
