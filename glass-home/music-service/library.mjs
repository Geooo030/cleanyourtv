import { parseLyrics } from './lyrics.mjs';
export class MusicLibrary {
  constructor(api,tracks,user){this.api=api;this.tracks=tracks;this.user=user;this.playlists=new Map();this.details=new Map();this.lyrics=new Map();}
  clear(){this.playlists.clear();this.details.clear();this.lyrics.clear();this.collectionSnapshot=null;this.collectionEpoch=(this.collectionEpoch||0)+1;}
  track(s){const t={id:s.id,name:s.name||'',artist:(s.ar||s.artists||[]).map(a=>a.name).join(' / '),album:(s.al||s.album)?.name||'',cover:(s.al||s.album)?.picUrl||'',duration:s.dt||s.duration||0};this.tracks.set(t.id,t);return t;}
  playlist(p){const item={id:p.id,name:p.name||'',cover:p.coverImgUrl||p.coverUrl||'',count:p.trackCount||0,description:p.description||'',owner:p.creator?.nickname||'',owned:Number(p.creator?.userId)>0&&Number(p.creator.userId)===Number(this.user()),subscribed:!!p.subscribed};this.playlists.set(item.id,item);return item;}
  async likedOrder(ids){
    const lists=await this.api('user_playlist',{uid:this.user(),limit:30,offset:0});
    const liked=lists.playlist?.find(p=>p.specialType===5);if(!liked)throw Error('LIKES_ORDER_UNAVAILABLE');
    const detail=await this.api('playlist_detail',{id:liked.id});
    if(!Array.isArray(detail.playlist?.trackIds))throw Error('LIKES_ORDER_UNAVAILABLE');
    const allowed=new Set(ids.map(Number)),seen=new Set();
    const ordered=detail.playlist.trackIds.map((item,index)=>({id:Number(item.id),at:Number(item.at)||0,index}))
      .filter(item=>allowed.has(item.id)).sort((a,b)=>b.at-a.at||a.index-b.index).map(item=>item.id).filter(id=>{if(seen.has(id))return false;seen.add(id);return true;});
    return ordered.concat(ids.map(Number).filter(id=>!seen.has(id)));
  }
  async collections(offset,kind='all'){
    if(!['all','saved','created'].includes(kind))throw Error('INVALID_COLLECTION_KIND');
    if(!this.collectionSnapshot||Date.now()-this.collectionTime>=300000){
      const epoch=this.collectionEpoch||0;
      if(!this.collectionPending||this.collectionPending.epoch!==epoch){
        const job={epoch,promise:null};this.collectionPending=job;
        job.promise=(async()=>{
          const raw=[],seen=new Set();let cursor=0;
          for(let page=0;page<100;page++){
            const body=await this.api('user_playlist',{uid:this.user(),limit:100,offset:cursor});
            if(!Array.isArray(body.playlist))throw Error('COLLECTIONS_FAILED');
            for(const p of body.playlist)if(p&&p.specialType!==5&&!seen.has(p.id)){seen.add(p.id);raw.push(p);}
            if(!body.more){if(epoch!==(this.collectionEpoch||0))throw Error('COLLECTIONS_CHANGED');this.collectionSnapshot=raw.map(p=>this.playlist(p));this.collectionTime=Date.now();return;}
            if(!body.playlist.length)throw Error('COLLECTIONS_FAILED');cursor+=body.playlist.length;
          }
          throw Error('COLLECTIONS_TOO_LARGE');
        })().finally(()=>{if(this.collectionPending===job)this.collectionPending=null;});
      }
      await this.collectionPending.promise;
    }
    return this.collectionPage(offset,kind);
  }
  collectionPage(offset,kind='all'){const list=this.collectionSnapshot.filter(p=>kind==='all'||(kind==='created'?p.owned:!p.owned));const items=list.slice(offset,offset+30);return {items,next:offset+items.length,hasMore:offset+items.length<list.length,total:list.length,kind};}
  async detail(id){
    let entry=this.details.get(id);if(entry&&Date.now()-entry.time<300000)return entry;
    const body=await this.api('playlist_detail',{id});if(!body.playlist)throw Error('PLAYLIST_FAILED');
    const p=body.playlist;entry={time:Date.now(),playlist:this.playlist(p),ids:(p.trackIds||p.tracks||[]).map(t=>t.id)};
    this.details.set(id,entry);return entry;
  }
  async playlistPage(id,offset){
    const entry=await this.detail(id),batch=entry.ids.slice(offset,offset+40);
    const body=batch.length?await this.api('song_detail',{ids:batch.join(',')}):{songs:[]};
    if(!Array.isArray(body.songs))throw Error('PLAYLIST_FAILED');
    const byId=new Map(body.songs.map(s=>[s.id,s]));
    return {playlist:entry.playlist,tracks:batch.map(id=>byId.get(id)).filter(Boolean).map(s=>this.track(s)),total:entry.ids.length,next:offset+batch.length,hasMore:offset+batch.length<entry.ids.length};
  }
  async search(q,type,offset){
    if(!q.trim()||q.length>100||!['song','playlist'].includes(type))throw Error('INVALID_SEARCH');
    const body=await this.api('cloudsearch',{keywords:q.trim(),type:type==='song'?1:1000,offset,limit:30,crypto:'weapi'});
    if(!body.result)throw Error('SEARCH_FAILED');
    const result=body.result,list=type==='song'?result.songs||[]:result.playlists||[],total=type==='song'?result.songCount||0:result.playlistCount||0;
    return {tracks:type==='song'?list.map(s=>this.track(s)):[],items:type==='playlist'?list.map(p=>this.playlist(p)):[],total,next:offset+list.length,hasMore:list.length>0&&offset+list.length<total};
  }
  async lyric(id){
    if(this.lyrics.has(id))return this.lyrics.get(id);
    let body;try{body=await this.api('lyric_new',{id,crypto:'weapi'});}catch{body=await this.api('lyric',{id,crypto:'weapi'});}
    const result=parseLyrics(body);this.lyrics.set(id,result);if(this.lyrics.size>100)this.lyrics.delete(this.lyrics.keys().next().value);return result;
  }
}
