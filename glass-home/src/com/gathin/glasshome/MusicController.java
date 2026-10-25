package com.gathin.glasshome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.LruCache;
import android.view.WindowManager;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Home-owned playback. No external music activity, cookie or WebView is used. */
final class MusicController {
    interface Listener { void changed(); }
    interface ImageResult { void loaded(Bitmap image); }
    interface Result { void loaded(JSONObject data,String error); }
    static final class Track {
        final long id; final String name,artist,album; final int duration;
        Track(JSONObject o){id=o.optLong("id");name=o.optString("name");artist=o.optString("artist");album=o.optString("album");duration=o.optInt("duration");}
        JSONObject json(){JSONObject value=new JSONObject();try{value.put("id",id).put("name",name).put("artist",artist).put("album",album).put("duration",duration);}catch(JSONException ignored){}return value;}
    }
    final ArrayList<Track> tracks=new ArrayList<>();
    final Handler main=new Handler(Looper.getMainLooper());
    private final GlassHomeActivity owner;
    private final ExecutorService network=Executors.newFixedThreadPool(3);
    private final ExecutorService artworkNetwork=Executors.newFixedThreadPool(2);
    private final HashMap<String,ArrayList<ImageResult>> pendingImages=new HashMap<>();
    private final ArrayList<Listener> listeners=new ArrayList<>();
    private final HashSet<Listener> pollingListeners=new HashSet<>();
    private final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(16*1024*1024){@Override protected int sizeOf(String id,Bitmap b){return b.getAllocationByteCount();}};
    private final ArtworkDiskCache artworkDisk;
    private final HashMap<String,Long> artworkTimes=new HashMap<>(),artworkRetry=new HashMap<>();
    private final Object artworkGuard=new Object();
    private volatile int artworkEpoch;
    private String artworkScope="";
    private final java.util.concurrent.atomic.AtomicInteger memoryHits=new java.util.concurrent.atomic.AtomicInteger(),diskHits=new java.util.concurrent.atomic.AtomicInteger(),imageRequests=new java.util.concurrent.atomic.AtomicInteger(),imageErrors=new java.util.concurrent.atomic.AtomicInteger();
    private volatile Track lastPlayed;
    private final PlaybackBookmark bookmark;
    private boolean restoring;
    private int pendingStartPosition;
    private final Runnable checkpoint=new Runnable(){public void run(){if(disposed||!playing)return;saveCheckpoint();main.postDelayed(this,5000);}};
    private final Object lastCoverLock=new Object();
    private final AudioManager audio;
    private final AudioFocusRequest audioFocus;
    private final MediaSession mediaSession;
    private long sessionTrackId=-1;
    private MediaPlayer player;
    private boolean disposed,statusBusy,pageBusy,preparing,foreground=true,resumeOnGain;
    private volatile int generation;
    private int nextOffset,queueRevision;
    private String queueRoute="/api/likes";
    JSONArray lyricLines=new JSONArray();boolean wordTimed;
    String lyricMessage="选择一首音乐";
    private float[] rhythm=new float[0];private int rhythmStep=50;
    private long sampledAt;private int sampledPosition;
    private final Object bufferLock=new Object();
    private final LinkedHashMap<Long,File> buffers=new LinkedHashMap<>();
    private Runnable afterPage;
    String phase="idle",message="登录网易云音乐",nickname="",playMessage="";
    String qr=""; Bitmap qrBitmap;
    int total,index=-1; boolean hasMore,playing;
    private String base="",token="";
    private final Runnable poll=new Runnable(){public void run(){if(disposed||pollingListeners.isEmpty())return;refresh();main.postDelayed(this,2500);}};
    MusicController(GlassHomeActivity owner){
        this.owner=owner;artworkDisk=new ArtworkDiskCache(new File(owner.getFilesDir(),"music-artwork"),128L*1024*1024);audio=(AudioManager)owner.getSystemService(android.content.Context.AUDIO_SERVICE);
        audioFocus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(change->{
                if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){if(player!=null)player.setVolume(.22f,.22f);}
                else if(change==AudioManager.AUDIOFOCUS_GAIN){if(player!=null)player.setVolume(1,1);if(resumeOnGain&&foreground){resumeOnGain=false;resume();}}
                else{boolean resume=change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT&&playing;pause(false);resumeOnGain=resume;}
            },main).build();
        mediaSession=new MediaSession(owner,"GlassHomeMusic");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setMediaButtonReceiver(null);
        mediaSession.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){if(!playing&&!preparing){if(player!=null)resume();else if(index>=0)play(index);}}
            @Override public void onPause(){pause(true);}
            @Override public void onStop(){pause(true);}
            @Override public void onSkipToNext(){next();}
            @Override public void onSkipToPrevious(){previous();}
            @Override public void onSeekTo(long position){seek((int)position-position());}
        },main);
        readConfig();
        try{lastPlayed=new Track(new JSONObject(owner.getSharedPreferences("music",0).getString("last_track","")));}catch(Exception ignored){}
        android.content.SharedPreferences saved=owner.getSharedPreferences("music",0);bookmark=new PlaybackBookmark(saved.getLong("last_position_track",0),saved.getInt("last_position",0));
    }
    private void readConfig(){android.content.SharedPreferences p=owner.getSharedPreferences("music",0);base=p.getString("base","");token=p.getString("token","");String scope=base+"|"+token;if(!scope.equals(artworkScope)){synchronized(artworkGuard){artworkEpoch++;artworkScope=scope;}images.evictAll();artworkTimes.clear();artworkRetry.clear();pendingImages.clear();}}
    void observe(Listener l){if(!listeners.contains(l))listeners.add(l);l.changed();}
    void add(Listener l){pollingListeners.add(l);readConfig();main.removeCallbacks(poll);main.post(poll);observe(l);}
    void remove(Listener l){listeners.remove(l);pollingListeners.remove(l);if(pollingListeners.isEmpty())main.removeCallbacks(poll);}
    private void emit(){if(disposed)return;updateMediaSession();for(Listener l:new ArrayList<>(listeners))l.changed();if(owner.scene!=null)owner.scene.musicChanged();}
    private void updateMediaSession(){
        Track track=current();boolean active=foreground&&track!=null;
        if(mediaSession.isActive()!=active)mediaSession.setActive(active);
        if(track!=null&&sessionTrackId!=track.id){
            sessionTrackId=track.id;
            mediaSession.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,String.valueOf(track.id))
                .putString(MediaMetadata.METADATA_KEY_TITLE,track.name).putString(MediaMetadata.METADATA_KEY_ARTIST,track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM,track.album).putLong(MediaMetadata.METADATA_KEY_DURATION,track.duration).build());
        }else if(track==null&&sessionTrackId!=-1){sessionTrackId=-1;mediaSession.setMetadata(null);}
        int state=track==null?PlaybackState.STATE_NONE:preparing?PlaybackState.STATE_BUFFERING:playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED;
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE
            |PlaybackState.ACTION_STOP|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO)
            .setState(state,position(),playing?1f:0f,android.os.SystemClock.elapsedRealtime()).build());
    }
    private JSONObject request(String route,boolean post)throws Exception{
        if(base.isEmpty())throw new IOException("音乐服务尚未连接");
        HttpURLConnection c=(HttpURLConnection)new URL(base+route).openConnection();
        c.setConnectTimeout(6000);c.setReadTimeout(22000);c.setRequestProperty("Authorization","Bearer "+token);
        if(post){c.setRequestMethod("POST");c.setDoOutput(true);c.getOutputStream().close();}
        try{int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();if(in==null)throw new IOException("音乐服务连接失败");
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2*1024*1024)throw new IOException("响应过大");}
            JSONObject result=new JSONObject(out.toString("UTF-8"));if(code>=400)throw new IOException(result.optString("error","音乐服务暂时不可用"));return result;
        }finally{c.disconnect();}
    }
    private String friendly(Exception e){String s=e.getMessage();return s!=null&&s.matches(".*[\\u4e00-\\u9fa5].*")?s:"音乐服务暂时不可用，请检查电脑连接";}
    void refresh(){
        if(statusBusy||disposed)return;statusBusy=true;
        network.execute(()->{try{JSONObject s=request("/api/status",false);main.post(()->{statusBusy=false;if(disposed)return;
            String previous=phase;phase=s.optString("phase","idle");message=s.optString("message");nickname=s.optString("nickname");total=s.optInt("count");
            String fresh=s.isNull("qrImage")?"":s.optString("qrImage","");if(!fresh.equals(qr)){qr=fresh;qrBitmap=null;if(qr.contains(",")){byte[] data=Base64.decode(qr.substring(qr.indexOf(',')+1),Base64.DEFAULT);qrBitmap=BitmapFactory.decodeByteArray(data,0,data.length);}}
            emit();if(phase.equals("idle"))login();
        });}catch(Exception e){main.post(()->{statusBusy=false;if(!phase.equals("ready")){phase="error";message=friendly(e);}emit();});}});
    }
    void login(){
        if(statusBusy)return;statusBusy=true;phase="starting";message="正在连接网易云音乐…";emit();
        network.execute(()->{try{request("/api/login",true);main.post(()->{statusBusy=false;refresh();});}catch(Exception e){main.post(()->{statusBusy=false;phase="error";message=friendly(e);emit();});}});
    }
    void logout(){
        invalidateArtwork();pause(true);bookmark.clear();restoring=false;pendingStartPosition=0;
        lastPlayed=null;owner.getSharedPreferences("music",0).edit().remove("last_track").remove("last_cover_id").remove("last_position_track").remove("last_position").apply();synchronized(lastCoverLock){new File(owner.getFilesDir(),"last-music-cover.jpg").delete();}
        pause(true);++generation;++queueRevision;pageBusy=false;preparing=false;releasePlayer();rhythm=new float[0];lyricLines=new JSONArray();network.execute(()->{try{request("/api/logout",true);main.post(()->{tracks.clear();images.evictAll();nextOffset=0;index=-1;hasMore=false;qr="";qrBitmap=null;phase="idle";refresh();});}catch(Exception e){main.post(()->{playMessage=friendly(e);emit();});}});
    }
    void loadMore(Runnable then){
        if(pageBusy){if(then!=null)afterPage=then;return;}if(disposed)return;pageBusy=true;afterPage=then;
        final int offset=nextOffset;
        final int version=queueRevision;final String route=queueRoute;
        network.execute(()->{try{JSONObject page=request(route+(route.contains("?")?"&":"?")+"offset="+offset,false);JSONArray list=page.getJSONArray("tracks");ArrayList<Track> batch=new ArrayList<>();for(int i=0;i<list.length();i++)batch.add(new Track(list.getJSONObject(i)));
            main.post(()->{if(disposed||version!=queueRevision)return;pageBusy=false;tracks.addAll(batch);nextOffset=page.optInt("next",offset);hasMore=page.optBoolean("hasMore");emit();Runnable r=afterPage;afterPage=null;if(r!=null)r.run();});
        }catch(Exception e){main.post(()->{if(disposed||version!=queueRevision)return;pageBusy=false;afterPage=null;playMessage=friendly(e);emit();});}});
    }
    Track current(){return index>=0&&index<tracks.size()?tracks.get(index):null;}
    Track previewTrack(){return current()!=null?current():lastPlayed;}
    boolean restoreLast(){if(current()!=null)return true;if(lastPlayed==null||lastPlayed.id<=0)return false;tracks.clear();tracks.add(lastPlayed);index=0;hasMore=false;nextOffset=0;restoring=true;pendingStartPosition=bookmark.forTrack(lastPlayed.id,lastPlayed.duration);sampledAt=0;playMessage="";emit();return true;}
    private void saveCheckpoint(){Track track=current();if(track==null||lastPlayed==null||track.id!=lastPlayed.id||preparing||player==null)return;bookmark.record(track.id,position(),duration());owner.getSharedPreferences("music",0).edit().putLong("last_position_track",bookmark.trackId).putInt("last_position",bookmark.position).apply();}
    int savedPosition(){return lastPlayed==null?0:bookmark.forTrack(lastPlayed.id,lastPlayed.duration);}

    void previewCover(Track track,ImageResult result){cover(track.id,result);}
    private void rememberCurrent(){
        Track track=current();if(track==null)return;
        if(lastPlayed!=null&&lastPlayed.id==track.id&&owner.getSharedPreferences("music",0).getLong("last_cover_id",-1)==track.id)return;
        lastPlayed=track;owner.getSharedPreferences("music",0).edit().putString("last_track",track.json().toString()).apply();
        cover(track.id,bitmap->{if(bitmap==null||disposed)return;artworkNetwork.execute(()->{synchronized(lastCoverLock){if(lastPlayed==null||lastPlayed.id!=track.id)return;File temp=new File(owner.getFilesDir(),"last-music-cover.tmp");try(FileOutputStream out=new FileOutputStream(temp)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,88,out))return;}catch(Exception ignored){return;}if(temp.renameTo(new File(owner.getFilesDir(),"last-music-cover.jpg")))owner.getSharedPreferences("music",0).edit().putLong("last_cover_id",track.id).apply();}});});
    }
    void fetch(String route,Result result){readConfig();network.execute(()->{try{JSONObject data=request(route,false);main.post(()->{if(!disposed)result.loaded(data,null);});}catch(Exception e){main.post(()->{if(!disposed)result.loaded(null,friendly(e));});}});}
    void queue(ArrayList<Track> source,int selected,String route,int next,boolean more){saveCheckpoint();restoring=false;pendingStartPosition=0;queueRevision++;pageBusy=false;tracks.clear();tracks.addAll(source);queueRoute=route;nextOffset=next;hasMore=more;afterPage=null;play(selected);}
    int playhead(){long now=android.os.SystemClock.uptimeMillis();if(now-sampledAt>180||!playing){sampledPosition=position();sampledAt=now;}return sampledPosition+(playing?(int)(now-sampledAt):0);}
    float level(int band){if(!playing||rhythm.length==0)return 0;float at=Math.max(0,(playhead()-band*37)/(float)rhythmStep);int i=Math.min(rhythm.length-1,(int)at),j=Math.min(rhythm.length-1,i+1);return rhythm[i]+(rhythm[j]-rhythm[i])*(at-(int)at);}
    int rhythmSamples(){return rhythm.length;}
    boolean busy(){return preparing;}
    int position(){try{return player!=null&&!preparing?player.getCurrentPosition():restoring?pendingStartPosition:0;}catch(IllegalStateException e){return 0;}}
    int duration(){try{return player!=null&&!preparing?player.getDuration():current()==null?0:current().duration;}catch(IllegalStateException e){return 0;}}
    void play(int selected){
        if(disposed||selected<0||selected>=tracks.size())return;
        saveCheckpoint();main.removeCallbacks(checkpoint);int start=restoring&&index==selected?pendingStartPosition:0;restoring=start>0;pendingStartPosition=start;
        final int currentGeneration=++generation;index=selected;preparing=true;playing=false;playMessage="正在载入…";releasePlayer();sampledAt=0;rhythm=new float[0];lyricLines=new JSONArray();lyricMessage="正在载入歌词…";emit();
        final long id=tracks.get(selected).id;
        fetch("/api/lyrics/"+id,(data,error)->{if(currentGeneration!=generation)return;lyricLines=data==null?new JSONArray():data.optJSONArray("lines");if(lyricLines==null)lyricLines=new JSONArray();wordTimed=data!=null&&data.optBoolean("wordTimed");lyricMessage=error!=null?"歌词暂时无法载入":lyricLines.length()==0?"此刻，让旋律说话":"";emit();});
        network.execute(()->{try{JSONObject result=request("/api/play/"+id,true);String route=result.getString("audio");
            if(!route.matches("/audio/[0-9]+"))throw new IOException("音频地址不可用");
            File buffer=buffer(id,route,currentGeneration);
            main.post(()->{if(disposed||currentGeneration!=generation||!foreground)return;prepare(buffer,currentGeneration);});
        }catch(Exception e){main.post(()->{if(currentGeneration!=generation||disposed)return;preparing=false;playMessage=friendly(e);emit();});}});
    }
    private File buffer(long id,String route,int version)throws Exception{
        synchronized(bufferLock){
            File cached=buffers.get(id);if(cached!=null&&cached.isFile())return cached;
            File file=File.createTempFile("music-buffer-",".audio",owner.getCacheDir());
            HttpURLConnection connection=(HttpURLConnection)new URL(base+route).openConnection();
            connection.setRequestProperty("Authorization","Bearer "+token);connection.setConnectTimeout(6000);connection.setReadTimeout(18000);
            boolean complete=false;
            try{
                if(connection.getResponseCode()!=200)throw new IOException("音频加载失败，请重试");
                long expected=connection.getContentLengthLong(),total=0;
                try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(file)){
                    byte[] block=new byte[32768];int n;while((n=in.read(block))!=-1){if(disposed||version!=generation)throw new IOException("CANCELLED");total+=n;if(total>64*1024*1024)throw new IOException("歌曲文件过大");out.write(block,0,n);}
                }
                if(total==0||(expected>=0&&expected!=total))throw new IOException("音频加载未完成，请重试");
                complete=true;buffers.put(id,file);
                while(buffers.size()>3){Long oldest=buffers.keySet().iterator().next();File old=buffers.remove(oldest);old.delete();}
                return file;
            }finally{connection.disconnect();if(!complete)file.delete();}
        }
    }
    private void prefetch(int version){
        if(index+1>=tracks.size())return;long id=tracks.get(index+1).id;
        network.execute(()->{try{if(version!=generation||disposed)return;JSONObject result=request("/api/play/"+id,true);String route=result.optString("audio");if(route.matches("/audio/[0-9]+"))buffer(id,route,version);}catch(Exception ignored){}});
    }
    private void prepare(File buffer,int version){
        try{
            player=new MediaPlayer();final MediaPlayer target=player;
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            // Xiaomi's VLC-backed MediaPlayer drops HTTP Authorization headers. Supply a
            // private file descriptor; only our Java HTTP client handles the paired stream.
            try(FileInputStream input=new FileInputStream(buffer)){player.setDataSource(input.getFD());}
            player.setOnPreparedListener(p->{if(version!=generation||disposed||p!=player)return;if(pendingStartPosition>0){int start=bookmark.forTrack(current().id,p.getDuration());p.setOnSeekCompleteListener(done->{if(version!=generation||disposed||done!=player)return;done.setOnSeekCompleteListener(null);startPrepared(version);});p.seekTo(start,MediaPlayer.SEEK_CLOSEST);}else startPrepared(version);});
            player.setOnCompletionListener(p->{if(p==player){playing=false;next();}});
            player.setOnErrorListener((p,what,extra)->{if(p==player){preparing=false;playing=false;playMessage="播放连接中断，按播放重试";releasePlayer();keepScreen(false);emit();}return true;});
            player.prepareAsync();
        }catch(Exception e){preparing=false;playMessage="暂时无法播放这首歌";releasePlayer();emit();}
    }
    private void startPrepared(int version){if(version!=generation||disposed||player==null)return;preparing=false;restoring=false;pendingStartPosition=0;playMessage="";sampledAt=0;resume();prefetch(version);long id=current().id;fetch("/api/rhythm/"+id,(data,error)->{if(version!=generation||data==null)return;JSONArray values=data.optJSONArray("values");if(values==null)return;float[] levels=new float[values.length()];for(int i=0;i<levels.length;i++)levels[i]=(float)values.optDouble(i,0);rhythmStep=data.optInt("step",50);rhythm=levels;emit();});}
    void toggle(){if(preparing)return;if(playing)pause(true);else if(player!=null)resume();else if(index>=0)play(index);else if(!tracks.isEmpty())play(0);}
    private void resume(){
        if(player==null||preparing||!foreground)return;
        if(audio.requestAudioFocus(audioFocus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){playMessage="当前音频正在被其他应用使用";emit();return;}
        try{player.start();playing=true;playMessage="";rememberCurrent();saveCheckpoint();main.removeCallbacks(checkpoint);main.postDelayed(checkpoint,5000);keepScreen(true);emit();}catch(IllegalStateException e){playMessage="播放连接中断，请重试";emit();}
    }
    void pause(boolean abandon){saveCheckpoint();main.removeCallbacks(checkpoint);resumeOnGain=false;if(player!=null&&!preparing)try{player.pause();}catch(IllegalStateException ignored){}playing=false;keepScreen(false);if(abandon)audio.abandonAudioFocusRequest(audioFocus);emit();}
    void next(){if(tracks.isEmpty())return;if(index+1<tracks.size())play(index+1);else if(hasMore)loadMore(()->play(index+1));else play(0);}
    void previous(){if(tracks.isEmpty())return;if(position()>3000){seek(-position());return;}play(Math.max(0,index-1));}
    void seek(int delta){if(player!=null&&!preparing)try{final int version=generation;player.setOnSeekCompleteListener(p->{if(p!=player||version!=generation)return;saveCheckpoint();sampledAt=0;emit();});player.seekTo(Math.max(0,Math.min(duration()-500,position()+delta)));sampledAt=0;}catch(IllegalStateException ignored){}}
    void foreground(boolean value){
        if(foreground==value)return;foreground=value;
        if(!value){
            pause(true);
            // Invalidate a cancelled audio load, but keep the prepared player's
            // pending lyric / rhythm results valid when leaving and returning.
            if(preparing){++generation;preparing=false;releasePlayer();playMessage="已暂停，按播放继续";emit();}
        }else updateMediaSession();
    }
    private void keepScreen(boolean enabled){if(enabled)owner.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else owner.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void releasePlayer(){if(player!=null){player.setOnPreparedListener(null);player.setOnSeekCompleteListener(null);player.setOnCompletionListener(null);player.setOnErrorListener(null);player.release();player=null;}}
    void cover(long id,ImageResult result){
        image("/cover/"+id,result);
    }
    void playlistCover(long id,ImageResult result){image("/playlist-cover/"+id,result);}
    void previewImage(String route,ImageResult result){if(route.matches("/home-preview-cover/(bilibili|aurora)/[a-zA-Z0-9]+(?:\\?v=[0-9]+)?"))image(route,result);else result.loaded(null);}
    private void image(String route,ImageResult result){
        if(disposed)return;readConfig();String key=artworkScope+"|"+route;long now=System.currentTimeMillis();Bitmap hit=images.get(key);
        if(hit!=null){memoryHits.incrementAndGet();result.loaded(hit);long fetched=artworkTimes.getOrDefault(key,0L);if(fetched<=now&&now-fetched<ArtworkDiskCache.FRESH_FOR)return;}
        if(artworkRetry.getOrDefault(key,0L)>android.os.SystemClock.elapsedRealtime()){if(hit==null)result.loaded(null);return;}
        if(pendingImages.containsKey(key)){pendingImages.get(key).add(result);return;}
        ArrayList<ImageResult> waiting=new ArrayList<>();waiting.add(result);pendingImages.put(key,waiting);
        final int epoch=artworkEpoch;final String address=base+route,authorization=token;
        artworkNetwork.execute(()->{
            if(disposed||epoch!=artworkEpoch)return;
            ArtworkDiskCache.Entry entry=artworkDisk.read(key,System.currentTimeMillis());
            if(entry==null&&route.startsWith("/cover/"))entry=legacyArtwork(route,key,epoch);
            Bitmap cached=entry==null?null:decodeArtwork(entry.bytes);
            if(entry!=null&&cached==null){artworkDisk.remove(key);entry=null;}
            if(cached!=null){diskHits.incrementAndGet();boolean fresh=entry.fresh(System.currentTimeMillis());deliverArtwork(key,cached,entry.fetchedAt,epoch,fresh);if(fresh)return;}
            if(disposed||epoch!=artworkEpoch)return;
            Bitmap bitmap=null;HttpURLConnection c=null;long fetched=0;
            try{
                imageRequests.incrementAndGet();c=(HttpURLConnection)new URL(address).openConnection();c.setRequestProperty("Authorization","Bearer "+authorization);c.setConnectTimeout(5000);c.setReadTimeout(10000);
                if(c.getResponseCode()!=200)throw new IOException("ARTWORK_UNAVAILABLE");byte[] bytes;
                try(InputStream in=c.getInputStream()){bytes=artworkBytes(in);}
                bitmap=decodeArtwork(bytes);if(bitmap==null)throw new IOException("INVALID_ARTWORK");fetched=System.currentTimeMillis();
                synchronized(artworkGuard){if(!disposed&&epoch==artworkEpoch)artworkDisk.write(key,bytes,fetched,fetched);}
            }catch(Exception ignored){imageErrors.incrementAndGet();}finally{if(c!=null)c.disconnect();}
            deliverArtwork(key,bitmap,fetched,epoch,true);
        });
    }
    private ArtworkDiskCache.Entry legacyArtwork(String route,String key,int epoch){
        long id;try{id=Long.parseLong(route.substring("/cover/".length()));}catch(Exception ignored){return null;}
        if(owner.getSharedPreferences("music",0).getLong("last_cover_id",-1)!=id)return null;
        synchronized(lastCoverLock){File file=new File(owner.getFilesDir(),"last-music-cover.jpg");if(!file.isFile())return null;try(InputStream in=new FileInputStream(file)){byte[] bytes=artworkBytes(in);long fetched=Math.max(1,Math.min(System.currentTimeMillis(),file.lastModified()));synchronized(artworkGuard){if(!disposed&&epoch==artworkEpoch)artworkDisk.write(key,bytes,fetched,System.currentTimeMillis());}return new ArtworkDiskCache.Entry(bytes,fetched);}catch(Exception ignored){return null;}}
    }
    private byte[] artworkBytes(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[16384];int n;while((n=in.read(block))!=-1){if(out.size()+n>ArtworkDiskCache.MAX_ENTRY)throw new IOException("ARTWORK_TOO_LARGE");out.write(block,0,n);}return out.toByteArray();}
    private Bitmap decodeArtwork(byte[] bytes){BitmapFactory.Options info=new BitmapFactory.Options();info.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,info);if(info.outWidth<=0||info.outHeight<=0)return null;BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(info.outWidth,info.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2;options.inPreferredConfig=Bitmap.Config.ARGB_8888;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);}
    private void deliverArtwork(String key,Bitmap bitmap,long fetched,int epoch,boolean finished){main.post(()->{
        if(disposed||epoch!=artworkEpoch)return;
        if(bitmap!=null){images.put(key,bitmap);artworkTimes.put(key,fetched);artworkRetry.remove(key);}
        ArrayList<ImageResult> callbacks=finished?pendingImages.remove(key):pendingImages.get(key);
        if(finished&&bitmap==null){if(artworkRetry.size()>200)artworkRetry.clear();artworkRetry.put(key,android.os.SystemClock.elapsedRealtime()+15000);if(images.get(key)!=null)return;}
        if(callbacks!=null)for(ImageResult callback:new ArrayList<>(callbacks))callback.loaded(bitmap);
    });}
    private void invalidateArtwork(){synchronized(artworkGuard){artworkEpoch++;}images.evictAll();artworkTimes.clear();artworkRetry.clear();pendingImages.clear();artworkNetwork.execute(()->{synchronized(artworkGuard){artworkDisk.clear();}});}
    String artworkSnapshot(){return "memoryHits="+memoryHits.get()+" diskHits="+diskHits.get()+" network="+imageRequests.get()+" errors="+imageErrors.get()+" pending="+pendingImages.size()+" memoryBytes="+images.size();}
    void close(){saveCheckpoint();disposed=true;artworkEpoch++;++generation;main.removeCallbacksAndMessages(null);listeners.clear();audio.abandonAudioFocusRequest(audioFocus);mediaSession.release();releasePlayer();network.shutdownNow();artworkNetwork.shutdownNow();pendingImages.clear();images.evictAll();File[] cached=owner.getCacheDir().listFiles();if(cached!=null)for(File f:cached)if(f.getName().startsWith("music-buffer-"))f.delete();}
}
