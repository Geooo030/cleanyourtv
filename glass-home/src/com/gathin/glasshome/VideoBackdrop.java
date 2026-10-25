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
    private final HandlerThread samplerThread=new HandlerThread("GlassBackdrop",android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private final Handler sampler;
    private final int[] blurPixels=new int[240*135],blurScratch=new int[240*135];
    private FrameListener listener;private MovieListener movieListener;
    private MediaPlayer player;private Surface surface;private ImageView cover,fallback;
    private boolean active,enabled=true,prepared,samplingPaused=true,rotation,samplePending,closed;
    private int sampleGeneration;
    private String movie="hawaii";
    private Bitmap heldFrame;
    private final Runnable sample=new Runnable(){@Override public void run(){
        if(!samplingPaused)requestGlassFrame();
    }};
    VideoBackdrop(Context context){super(context);samplerThread.start();sampler=new Handler(samplerThread.getLooper());setOpaque(true);setAlpha(0);setSurfaceTextureListener(this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setFrameListener(FrameListener listener){this.listener=listener;}
    void setMovieListener(MovieListener listener){movieListener=listener;}
    void setCover(ImageView cover){this.cover=cover;}
    void setFallback(ImageView fallback){this.fallback=fallback;}
    private void showVideo(boolean visible){setAlpha(visible?1:0);if(fallback!=null)fallback.setVisibility(visible?View.INVISIBLE:View.VISIBLE);}
    String currentMovie(){return movie;}
    void setSamplingPaused(boolean value){samplingPaused=value;handler.removeCallbacks(sample);if(!value&&active&&prepared)handler.postDelayed(sample,400);}
    /** Sample only for panels or a movie handoff; PixelCopy and blur run off the UI thread. */
    void requestGlassFrame(){
        if(closed||samplePending||!active||!prepared||surface==null||!surface.isValid())return;
        samplePending=true;final int token=sampleGeneration;final Surface source=surface;
        sampler.post(()->{
            Bitmap frame=Bitmap.createBitmap(240,135,Bitmap.Config.ARGB_8888);
            try{PixelCopy.request(source,frame,result->{
                if(result==PixelCopy.SUCCESS)GlassScene.blurFrame(frame,frame,blurPixels,blurScratch);
                handler.post(()->{
                    samplePending=false;
                    try{if(!closed&&active&&token==sampleGeneration&&result==PixelCopy.SUCCESS&&listener!=null)listener.frame(frame);}
                    finally{frame.recycle();}
                    if(!closed&&active&&prepared&&!samplingPaused){handler.removeCallbacks(sample);handler.postDelayed(sample,400);}
                });
            },sampler);}
            catch(IllegalArgumentException error){frame.recycle();handler.post(()->{samplePending=false;if(!closed&&!samplingPaused)handler.postDelayed(sample,400);});}
        });
    }
    void setRotation(boolean value){rotation=value;if(player!=null&&prepared)player.setLooping(!rotation||WallpaperCatalog.next(getContext(),movie).equals(movie));}
    void select(String id){
        if(movie.equals(id))return;
        if(!WallpaperCatalog.find(id).file(getContext()).isFile())return;
        holdFrame();releasePlayer();movie=id;if(movieListener!=null)movieListener.changed(id);if(active&&enabled)start();
    }
    void setPlaybackEnabled(boolean value){enabled=value;if(!value){stop();showVideo(false);clearCover();}}
    void start(){
        active=true;if(!enabled||!isAvailable())return;
        if(player!=null&&prepared){player.start();showVideo(true);fadeCover();handler.removeCallbacks(sample);handler.postDelayed(sample,400);return;}
        if(player!=null)return;
        java.io.File file=WallpaperCatalog.find(movie).file(getContext());if(!file.isFile())return;
        try{
            player=new MediaPlayer();player.setDataSource(file.getAbsolutePath());surface=new Surface(getSurfaceTexture());player.setSurface(surface);player.setVolume(0,0);
            player.setLooping(!rotation||WallpaperCatalog.next(getContext(),movie).equals(movie));
            player.setOnPreparedListener(mp->{prepared=true;fit(mp.getVideoWidth(),mp.getVideoHeight());android.util.Log.i("GlassWallpaper","Prepared "+movie+" rotation="+rotation);if(active&&enabled)mp.start();});
            player.setOnInfoListener((mp,what,extra)->{if(what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START&&active&&enabled){showVideo(true);fadeCover();requestGlassFrame();}return false;});
            player.setOnCompletionListener(mp->{if(!active||!enabled)return;String next=rotation?WallpaperCatalog.next(getContext(),movie):movie;if(next.equals(movie)){mp.seekTo(0);mp.start();}else select(next);});
            player.setOnErrorListener((mp,what,extra)->{releasePlayer();clearCover();showVideo(false);return true;});player.prepareAsync();
        }catch(Exception error){releasePlayer();clearCover();showVideo(false);}
    }
    private void holdFrame(){
        if(cover==null||!enabled||!prepared||!isAvailable())return;
        cover.animate().cancel();Bitmap snapshot=getBitmap(960,540);if(snapshot==null)return;
        Bitmap previous=heldFrame;heldFrame=snapshot;cover.setImageBitmap(snapshot);cover.setAlpha(1);if(previous!=null)previous.recycle();
    }
    private void fadeCover(){if(cover!=null&&heldFrame!=null){cover.animate().cancel();cover.animate().alpha(0).setDuration(950).setInterpolator(new android.view.animation.PathInterpolator(.4f,0,.2f,1)).withEndAction(this::clearCover).start();}}
    private void clearCover(){if(cover!=null){cover.animate().cancel();cover.setAlpha(0);cover.setImageDrawable(null);}if(heldFrame!=null){heldFrame.recycle();heldFrame=null;}}
    void stop(){active=false;sampleGeneration++;handler.removeCallbacks(sample);animate().cancel();if(cover!=null)cover.animate().cancel();if(player!=null&&prepared&&player.isPlaying())player.pause();}
    private void fit(int vw,int vh){if(vw==0||vh==0||getWidth()==0||getHeight()==0)return;float vr=vw/(float)vh,sr=getWidth()/(float)getHeight();Matrix matrix=new Matrix();matrix.setScale(Math.max(1,vr/sr),Math.max(1,sr/vr),getWidth()/2f,getHeight()/2f);setTransform(matrix);}
    private void releasePlayer(){sampleGeneration++;handler.removeCallbacks(sample);prepared=false;if(player!=null){player.release();player=null;}if(surface!=null){surface.release();surface=null;}}
    void close(){closed=true;stop();releasePlayer();clearCover();samplerThread.quitSafely();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int w,int h){if(active)start();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int w,int h){if(player!=null&&prepared)fit(player.getVideoWidth(),player.getVideoHeight());}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){releasePlayer();showVideo(false);return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){}
}
