import test from 'node:test';
import assert from 'node:assert/strict';
import {HomePreviews,parseBilibili,parseTencent,previewImageUrl} from './home-previews.mjs';
const bili={code:0,data:{list:[{bvid:'BV123abc',title:'Video',owner:{name:'Creator'},pic:'http://i0.hdslb.com/bfs/a.jpg'},{id:123,title:'Ad'}]}};
test('preview providers retain titles and reject foreign image hosts',()=>{
  assert.equal(parseBilibili(bili)[0].title,'Video');assert.equal(parseBilibili(bili).length,1);assert.equal(previewImageUrl('http://i0.hdslb.com/bfs/a.jpg').protocol,'https:');
  for(const url of ['https://i0.hdslb.com.evil.test/a','http://127.0.0.1/a','https://user@i0.hdslb.com/a','https://i0.hdslb.com:8080/a'])assert.throws(()=>previewImageUrl(url));
});
test('Tencent string fields are decoded without executing page scripts',()=>{
  const html='cid:"show123",title:"",smallCoverPic:"https://vcover-hz-pic.puui.qpic.cn/a",mzTitle:"A \\"quoted\\" title",subTitle:"New series";globalThis.previewExecuted=true;';
  assert.equal(parseTencent(html)[0].title,'A "quoted" title');assert.equal(globalThis.previewExecuted,undefined);
  const ld='<script type="application/ld+json">'+JSON.stringify({'@graph':[{'@type':'ItemList',itemListElement:[{item:{name:'Show',url:'https://v.qq.com/x/cover/show321.html',image:'https://tv.puui.qpic.cn/a'}}]}]})+'</script>';
  assert.equal(parseTencent(ld)[0].id,'show321');assert.throws(()=>parseTencent('no feed'));
});
test('Tencent prefers the large cover and replaces the old thumbnail transform',()=>{
  const large='https://tv.puui.qpic.cn/tv/0/large/0?imageMogr2/thumbnail/1800x/quality/75&max_age=7776000';
  const small='https://vcover-hz-pic.puui.qpic.cn/vcover_hz_pic/0/small/0?imageMogr2/thumbnail/175x';
  const html='cid:"showhd",title:"HD",smallCoverPic:'+JSON.stringify(small)+',coverPic:'+JSON.stringify(large);
  const item=parseTencent(html)[0];assert.equal(item.image,'https://tv.puui.qpic.cn/tv/0/large/0?imageMogr2/thumbnail/960x/quality/90');
  const fallback=parseTencent('cid:"showsmall",title:"Fallback",smallCoverPic:'+JSON.stringify(small))[0];
  assert.equal(new URL(fallback.image).search,'?imageMogr2/thumbnail/960x/quality/90');
  assert.ok(!fallback.image.includes('175x'));
});
test('cached previews survive upstream failure with their original timestamp',async()=>{
  let now=100,calls=0;const service=new HomePreviews({now:()=>now,fetcher:async()=>{if(++calls>1)throw Error('offline');return new Response(JSON.stringify(bili));}});
  const first=await service.feed('bilibili');assert.equal(first.items[0].cover,'/home-preview-cover/bilibili/BV123abc?v=3');assert.equal(first.source,'哔哩哔哩热门');assert.equal((await service.feed('bilibili')).stale,false);assert.equal(calls,1);
  now+=31*60*1000;const stale=await service.feed('bilibili');assert.equal(stale.stale,true);assert.equal(stale.updatedAt,100);
  await assert.rejects(()=>service.feed('other'),/INVALID_PREVIEW_PROVIDER/);
});
test('poster redirect destinations are checked before another request',async()=>{
  let calls=0;const service=new HomePreviews({fetcher:async()=>{calls++;return calls===1?new Response(JSON.stringify(bili)):new Response(null,{status:302,headers:{location:'http://127.0.0.1/private'}});}});
  await service.feed('bilibili');await assert.rejects(()=>service.cover('bilibili','BV123abc'),/INVALID_PREVIEW_IMAGE/);assert.equal(calls,2);
});
