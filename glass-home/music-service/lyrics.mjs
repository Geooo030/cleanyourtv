export function parseLyrics(result) {
  const enhanced=[];
  for(const row of (result?.yrc?.lyric||'').split('\n')){
    const header=row.match(/^\[(\d+),(\d+)\]/);if(!header)continue;
    const words=[...row.slice(header[0].length).matchAll(/\((\d+),(\d+),\d+\)([^()]*)/g)]
      .map(m=>({start:Number(m[1]),end:Number(m[1])+Number(m[2]),text:m[3]}));
    const text=words.map(w=>w.text).join('');if(text.trim())enhanced.push({start:Number(header[1]),end:Number(header[1])+Number(header[2]),text,words});
  }
  if(enhanced.length)return {wordTimed:true,lines:enhanced.sort((a,b)=>a.start-b.start)};
  const lines=[],source=result?.lrc?.lyric||'';
  const offset=Number(source.match(/\[offset:([+-]?\d+)\]/i)?.[1]||0);
  for(const row of source.split('\n')){
    const times=[...row.matchAll(/\[(\d+):(\d+)(?:[.:](\d+))?\]/g)];
    const text=row.replace(/\[[^\]]*\]/g,'').trim();if(!times.length||!text)continue;
    for(const t of times)lines.push({start:Math.max(0,Number(t[1])*60000+Number(t[2])*1000+Math.round(Number('0.'+(t[3]||0))*1000)+offset),text,words:[]});
  }
  lines.sort((a,b)=>a.start-b.start);lines.forEach((l,i)=>l.end=lines[i+1]?.start||l.start+7000);
  return {wordTimed:false,lines};
}
