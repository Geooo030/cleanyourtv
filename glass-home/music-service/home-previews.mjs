import fs from 'node:fs';
import path from 'node:path';

const SOURCES={bilibili:{url:'https://api.bilibili.com/x/web-interface/popular?ps=6&pn=1',source:'哔哩哔哩热门',referer:'https://www.bilibili.com/'},aurora:{url:'https://v.qq.com/channel/tv',source:'腾讯视频电视剧热播',referer:'https://v.qq.com/'}};
const CACHE_VERSION=3;
const TTL=30*60*1000;
const imageHost=host=>/^i\d\.hdslb\.com$/.test(host)||['tv.puui.qpic.cn','vcover-hz-pic.puui.qpic.cn','puui.qpic.cn'].includes(host);
export function previewImageUrl(value){
  const url=new URL(value.startsWith('//')?'https:'+value:value);
  if(!imageHost(url.hostname)||url.username||url.password||url.port||!['http:','https:'].includes(url.protocol))throw Error('INVALID_PREVIEW_IMAGE');
  url.protocol='https:';return url;
}
const text=value=>String(value||'').replace(/[\u0000-\u001f]/g,' ').trim().slice(0,180);
export function parseBilibili(body){
  if(body?.code!==0||!Array.isArray(body.data?.list))throw Error('PREVIEW_UNAVAILABLE');
  return body.data.list.filter(item=>/^BV\w+$/.test(item.bvid)&&item.pic&&item.title).slice(0,6).map(item=>({id:String(item.bvid),title:text(item.title),subtitle:text(item.owner?.name),image:previewImageUrl(item.pic).toString()}));
}
function tencentPoster(value){
  const url=previewImageUrl(value);
  // Qpic uses a bare imageMogr2 command, not an imageMogr2= query parameter.
  // Replace old 175px commands completely instead of appending a second transform.
  url.search='?imageMogr2/thumbnail/960x/quality/90';return url.toString();
}
// Read string literals only; never execute the page's embedded JavaScript.
function field(block,name){const found=block.match(new RegExp('\\b'+name+':("(?:[^"\\\\]|\\\\.)*")'));try{return found?JSON.parse(found[1]):'';}catch{return '';}}
export function parseTencent(html){
  const items=[],seen=new Set(),matches=[...html.matchAll(/\bcid:"([a-zA-Z0-9]+)"/g)];
  for(let i=0;i<matches.length&&items.length<6;i++){
    const id=matches[i][1],block=html.slice(matches[i].index,matches[i+1]?.index||matches[i].index+6000);
    const title=text(field(block,'mzTitle')||field(block,'sourceTitle')||field(block,'title'));
    const image=field(block,'coverPic')||field(block,'smallCoverPic');
    if(seen.has(id)||!title||!image)continue;
    seen.add(id);items.push({id,title,subtitle:text(field(block,'subTitle')),image:tencentPoster(image)});
  }
  if(!items.length){
    for(const match of html.matchAll(/<script[^>]*type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/g)){
      let data;try{data=JSON.parse(match[1]);}catch{continue;}
      for(const graph of data['@graph']||[])for(const entry of graph.itemListElement||[]){const item=entry.item,id=item?.url?.match(/\/cover\/(\w+)\.html/)?.[1];if(!id||!item.image||seen.has(id))continue;seen.add(id);items.push({id,title:text(item.name),subtitle:text(item.description),image:tencentPoster(item.image)});}
    }
  }
  if(!items.length)throw Error('PREVIEW_UNAVAILABLE');return items.slice(0,6);
}
async function limited(response,limit){
  if(!response.ok)throw Error('PREVIEW_UNAVAILABLE');let size=0;const chunks=[];
  for await(const chunk of response.body){size+=chunk.length;if(size>limit)throw Error('PREVIEW_TOO_LARGE');chunks.push(Buffer.from(chunk));}
  return Buffer.concat(chunks);
}
export class HomePreviews {
  constructor({fetcher=fetch,now=Date.now,directory}={}){
    this.fetcher=fetcher;this.now=now;this.directory=directory;this.cache=new Map();this.pending=new Map();this.images=new Map();
    if(directory)for(const provider of Object.keys(SOURCES))try{const value=JSON.parse(fs.readFileSync(path.join(directory,'home-preview-'+provider+'.json'),'utf8'));if(value.schema===CACHE_VERSION&&Array.isArray(value.items)&&Number.isFinite(value.updatedAt)){value.items.forEach(item=>previewImageUrl(item.image));this.cache.set(provider,value);}}catch{}
  }
  async feed(provider){
    if(!Object.hasOwn(SOURCES,provider))throw Error('INVALID_PREVIEW_PROVIDER');
    const old=this.cache.get(provider);if(old&&this.now()-old.updatedAt<TTL)return this.public(provider,old,false);
    if(!this.pending.has(provider)){
      const job=this.refresh(provider).catch(error=>{if(old)return this.public(provider,old,true);throw error;}).finally(()=>this.pending.delete(provider));this.pending.set(provider,job);
    }
    return this.pending.get(provider);
  }
  public(provider,value,stale){return {provider,source:SOURCES[provider].source,updatedAt:value.updatedAt,stale,items:value.items.map(({image,...item})=>({...item,cover:'/home-preview-cover/'+provider+'/'+item.id+'?v='+CACHE_VERSION}))};}
  async refresh(provider){
    const source=SOURCES[provider],response=await this.fetcher(source.url,{headers:{'User-Agent':'Mozilla/5.0','Referer':source.referer},signal:AbortSignal.timeout(10000)});
    const input=(await limited(response,3*1024*1024)).toString('utf8');
    const value={schema:CACHE_VERSION,updatedAt:this.now(),items:provider==='bilibili'?parseBilibili(JSON.parse(input)):parseTencent(input)};
    if(!value.items.length)throw Error('PREVIEW_UNAVAILABLE');this.cache.set(provider,value);
    if(this.directory)try{fs.writeFileSync(path.join(this.directory,'home-preview-'+provider+'.json'),JSON.stringify(value));}catch{}
    return this.public(provider,value,false);
  }
  async cover(provider,id){
    if(!Object.hasOwn(SOURCES,provider))throw Error('INVALID_PREVIEW_PROVIDER');
    const key=provider+':'+id;if(this.images.has(key))return this.images.get(key);
    if(!this.cache.has(provider))await this.feed(provider);
    const item=this.cache.get(provider)?.items.find(item=>item.id===id);if(!item)throw Error('UNKNOWN_PREVIEW_ITEM');
    let url=previewImageUrl(item.image);
    if(provider==='bilibili')url.pathname+='@640w_360h_1c.webp';
    for(let hop=0;hop<3;hop++){
      const response=await this.fetcher(url,{headers:{'Referer':SOURCES[provider].referer,'User-Agent':'Mozilla/5.0'},redirect:'manual',signal:AbortSignal.timeout(10000)});
      if(response.status>=300&&response.status<400){await response.body?.cancel();url=previewImageUrl(new URL(response.headers.get('location'),url).toString());continue;}
      const type=response.headers.get('content-type')||'';if(!/^image\//i.test(type))throw Error('INVALID_PREVIEW_IMAGE');
      const image={type,body:await limited(response,2*1024*1024)};this.images.set(key,image);if(this.images.size>20)this.images.delete(this.images.keys().next().value);return image;
    }
    throw Error('PREVIEW_REDIRECT');
  }
}
