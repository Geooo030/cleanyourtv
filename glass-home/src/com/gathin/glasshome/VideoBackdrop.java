package com.gathin.glasshome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import java.io.File;

/** One hardware-decoded local video, paused as soon as Home is hidden. No streaming or background service. */
final class VideoBackdrop extends TextureView implements TextureView.SurfaceTextureListener {
    interface FrameListener { void frame(Bitmap frame); }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameListener listener;
    private MediaPlayer player;
    private Surface surface;
    private boolean active, enabled = true, prepared;
    private Bitmap smallFrame;
    private final Runnable sample = new Runnable() {
        @Override public void run() {
            if (!active || !prepared || !isAvailable()) return;
            if (smallFrame == null) smallFrame = Bitmap.createBitmap(240,135,Bitmap.Config.ARGB_8888);
            Bitmap frame = getBitmap(smallFrame);
            if (frame != null && listener != null) listener.frame(frame);
            handler.postDelayed(this, 400);
        }
    };

    VideoBackdrop(Context context) {
        super(context);
        setOpaque(false);
        setAlpha(0f);
        setSurfaceTextureListener(this);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void setFrameListener(FrameListener listener) { this.listener = listener; }
    void setPlaybackEnabled(boolean value) {
        enabled = value;
        if (!value) { stop(); setAlpha(0f); }
    }
    void start() {
        active = true;
        if (!enabled || !isAvailable()) return;
        if (player != null && prepared) {
            player.start();
            setAlpha(1f);
            handler.removeCallbacks(sample);
            handler.post(sample);
            return;
        }
        if (player != null) return;
        File file = new File(getContext().getExternalFilesDir(null), "aerial.mov");
        if (!file.isFile()) return;
        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            surface = new Surface(getSurfaceTexture());
            player.setSurface(surface);
            player.setVolume(0,0);
            player.setLooping(true);
            player.setOnPreparedListener(mp -> {
                prepared = true;
                fit(mp.getVideoWidth(),mp.getVideoHeight());
                if (active && enabled) mp.start();
            });
            player.setOnInfoListener((mp,what,extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && active && enabled) {
                    animate().alpha(1f).setDuration(650).start();
                    handler.removeCallbacks(sample);
                    handler.postDelayed(sample,200);
                }
                return false;
            });
            player.setOnErrorListener((mp,what,extra) -> { releasePlayer(); setAlpha(0f); return true; });
            player.prepareAsync();
        } catch (Exception ex) { releasePlayer(); setAlpha(0f); }
    }
    void stop() {
        active = false;
        handler.removeCallbacks(sample);
        animate().cancel();
        if (player != null && prepared && player.isPlaying()) player.pause();
    }
    private void fit(int vw, int vh) {
        if (vw==0 || vh==0 || getWidth()==0 || getHeight()==0) return;
        float videoRatio=vw/(float)vh, viewRatio=getWidth()/(float)getHeight();
        Matrix matrix=new Matrix();
        matrix.setScale(Math.max(1f,videoRatio/viewRatio), Math.max(1f,viewRatio/videoRatio), getWidth()/2f,getHeight()/2f);
        setTransform(matrix);
    }
    private void releasePlayer() {
        handler.removeCallbacks(sample);
        prepared=false;
        if (player!=null) { player.release(); player=null; }
        if (surface!=null) { surface.release(); surface=null; }
    }
    void close() { stop(); releasePlayer(); if (smallFrame!=null) smallFrame.recycle(); }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h) { if(active) start(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h) { if (player!=null && prepared) fit(player.getVideoWidth(),player.getVideoHeight()); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { releasePlayer(); setAlpha(0f); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
}
