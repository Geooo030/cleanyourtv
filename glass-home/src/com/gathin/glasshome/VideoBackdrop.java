package com.gathin.glasshome;

import android.content.Context;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.ImageView;

/** Single decoder; a held last frame covers the handoff until the next movie renders. */
final class VideoBackdrop extends TextureView implements TextureView.SurfaceTextureListener {
    interface FrameListener { void frame(Bitmap frame); }
    interface MovieListener { void changed(String id); }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private FrameListener listener;private MovieListener movieListener;
    private MediaPlayer player;private Surface surface;private ImageView cover;
    private boolean active,enabled=true,prepared,samplingPaused,rotation;
    private String movie="hawaii";
    private Bitmap smallFrame,heldFrame;
    private final Runnable sample=new Runnable(){@Override public void run(){
        if(!active||!prepared||samplingPaused||!isAvailable())return;
        if(smallFrame==null)smallFrame=Bitmap.createBitmap(240,135,Bitmap.Config.ARGB_8888);
        Bitmap frame=getBitmap(smallFrame);if(frame!=null&&listener!=null)listener.frame(frame);handler.postDelayed(this,400);
    }};
    VideoBackdrop(Context context){super(context);setOpaque(false);setAlpha(0);setSurfaceTextureListener(this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setFrameListener(FrameListener listener){this.listener=listener;}
    void setMovieListener(MovieListener listener){movieListener=listener;}
    void setCover(ImageView cover){this.cover=cover;}
    Bitmap currentFrame(){return smallFrame;}
    String currentMovie(){return movie;}
    void setSamplingPaused(boolean value){samplingPaused=value;handler.removeCallbacks(sample);if(!value&&active&&prepared)handler.postDelayed(sample,400);}
    void setRotation(boolean value){rotation=value;if(player!=null&&prepared)player.setLooping(!rotation||WallpaperCatalog.next(getContext(),movie).equals(movie));}
    void select(String id){
        if(movie.equals(id))return;
        if(!WallpaperCatalog.find(id).file(getContext()).isFile())return;
        holdFrame();releasePlayer();movie=id;if(movieListener!=null)movieListener.changed(id);if(active&&enabled)start();
    }
    void setPlaybackEnabled(boolean value){enabled=value;if(!value){stop();setAlpha(0);clearCover();}}
    void start(){
        active=true;if(!enabled||!isAvailable())return;
        if(player!=null&&prepared){player.start();setAlpha(1);fadeCover();handler.removeCallbacks(sample);handler.postDelayed(sample,400);return;}
        if(player!=null)return;
        java.io.File file=WallpaperCatalog.find(movie).file(getContext());if(!file.isFile())return;
        try{
            player=new MediaPlayer();player.setDataSource(file.getAbsolutePath());surface=new Surface(getSurfaceTexture());player.setSurface(surface);player.setVolume(0,0);
            player.setLooping(!rotation||WallpaperCatalog.next(getContext(),movie).equals(movie));
            player.setOnPreparedListener(mp->{prepared=true;fit(mp.getVideoWidth(),mp.getVideoHeight());android.util.Log.i("GlassWallpaper","Prepared "+movie+" rotation="+rotation);if(active&&enabled)mp.start();});
            player.setOnInfoListener((mp,what,extra)->{if(what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START&&active&&enabled){setAlpha(1);fadeCover();handler.removeCallbacks(sample);handler.postDelayed(sample,400);}return false;});
            player.setOnCompletionListener(mp->{if(!active||!enabled)return;String next=rotation?WallpaperCatalog.next(getContext(),movie):movie;if(next.equals(movie)){mp.seekTo(0);mp.start();}else select(next);});
            player.setOnErrorListener((mp,what,extra)->{releasePlayer();clearCover();setAlpha(0);return true;});player.prepareAsync();
        }catch(Exception error){releasePlayer();clearCover();setAlpha(0);}
    }
    private void holdFrame(){
        if(cover==null||!enabled||!prepared||!isAvailable())return;
        cover.animate().cancel();Bitmap snapshot=getBitmap(960,540);if(snapshot==null)return;
        Bitmap previous=heldFrame;heldFrame=snapshot;cover.setImageBitmap(snapshot);cover.setAlpha(1);if(previous!=null)previous.recycle();
    }
    private void fadeCover(){if(cover!=null&&heldFrame!=null){cover.animate().cancel();cover.animate().alpha(0).setDuration(950).setInterpolator(new android.view.animation.PathInterpolator(.4f,0,.2f,1)).withEndAction(this::clearCover).start();}}
    private void clearCover(){if(cover!=null){cover.animate().cancel();cover.setAlpha(0);cover.setImageDrawable(null);}if(heldFrame!=null){heldFrame.recycle();heldFrame=null;}}
    void stop(){active=false;handler.removeCallbacks(sample);animate().cancel();if(cover!=null)cover.animate().cancel();if(player!=null&&prepared&&player.isPlaying())player.pause();}
    private void fit(int vw,int vh){if(vw==0||vh==0||getWidth()==0||getHeight()==0)return;float vr=vw/(float)vh,sr=getWidth()/(float)getHeight();Matrix matrix=new Matrix();matrix.setScale(Math.max(1,vr/sr),Math.max(1,sr/vr),getWidth()/2f,getHeight()/2f);setTransform(matrix);}
    private void releasePlayer(){handler.removeCallbacks(sample);prepared=false;if(player!=null){player.release();player=null;}if(surface!=null){surface.release();surface=null;}}
    void close(){stop();releasePlayer();clearCover();if(smallFrame!=null)smallFrame.recycle();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int w,int h){if(active)start();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int w,int h){if(player!=null&&prepared)fit(player.getVideoWidth(),player.getVideoHeight());}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){releasePlayer();setAlpha(0);return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){}
}
