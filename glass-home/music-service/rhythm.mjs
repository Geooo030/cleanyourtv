import { spawn } from 'node:child_process';
/** Real PCM energy at 20 Hz. No microphone access or invented beat animation. */
export async function rhythm(response){
  const child=spawn('ffmpeg',['-v','error','-i','pipe:0','-vn','-ac','1','-ar','8000','-f','f32le','pipe:1'],{windowsHide:true,stdio:['pipe','pipe','pipe']});
  const samples=[];let carry=Buffer.alloc(0),sum=0,count=0,total=0;
  child.stderr.resume();child.stdin.on('error',()=>{});
  child.stdout.on('data',chunk=>{
    const input=Buffer.concat([carry,chunk]),end=input.length-input.length%4;
    for(let i=0;i<end;i+=4){const v=input.readFloatLE(i);sum+=v*v;if(++count===400){samples.push(Math.sqrt(sum/400));sum=0;count=0;}}
    carry=input.subarray(end);total+=chunk.length;if(total>80*1024*1024)child.kill();
  });
  const done=new Promise((resolve,reject)=>{child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(Error('ANALYSIS_FAILED')));});
  done.catch(()=>{}); // A missing FFmpeg can fail before the stream import completes.
  const timeout=setTimeout(()=>child.kill(),25000);
  try{
    const {Readable}=await import('node:stream');const stream=Readable.fromWeb(response.body);stream.on('error',()=>child.kill());stream.pipe(child.stdin);await done;
    const sorted=[...samples].sort((a,b)=>a-b),peak=sorted[Math.floor(sorted.length*.96)]||1;
    return {step:50,values:samples.map(v=>Math.round(Math.min(1,Math.sqrt(v/peak))*1000)/1000)};
  }finally{clearTimeout(timeout);child.kill();}
}
