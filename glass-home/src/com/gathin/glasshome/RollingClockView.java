package com.gathin.glasshome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.PathInterpolator;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Only this small display list changes during a minute rollover. */
final class RollingClockView extends View {
    static final float HEIGHT=138, BASELINE=112;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final float digitWidth,colonWidth,designWidth;
    private String current="",previous="";
    private long lastMinute=Long.MIN_VALUE;
    private float progress=1;
    private boolean running;
    private ValueAnimator animation;
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(!running)return;
            long now=System.currentTimeMillis();
            update(now,true);
            // Recheck wall time once a second, but redraw only when the minute changes.
            handler.postDelayed(this,1000-now%1000);
        }
    };

    RollingClockView(Context context){
        super(context);
        paint.setTypeface(GlassFonts.create(context,600));
        paint.setTextSize(120);
        paint.setFontFeatureSettings("tnum");
        paint.setColor(Color.WHITE);
        digitWidth=paint.measureText("0");colonWidth=paint.measureText(":");
        designWidth=4*digitWidth+colonWidth;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        update(System.currentTimeMillis(),false);
    }

    float designWidth(){return designWidth;}
    void start(){
        stop();running=true;
        update(System.currentTimeMillis(),false);
        handler.post(tick);
    }
    void stop(){
        running=false;handler.removeCallbacks(tick);
        if(animation!=null){animation.cancel();animation=null;}
        previous=current;progress=1;invalidate();
    }

    private void update(long now,boolean animate){
        long minute=now/60000;
        String next=new SimpleDateFormat("HH:mm",Locale.US).format(new Date(now));
        if(next.equals(current)){lastMinute=minute;return;}
        if(animation!=null){animation.cancel();animation=null;}
        boolean roll=animate&&minute-lastMinute==1&&current.length()==5&&ValueAnimator.areAnimatorsEnabled();
        previous=current;current=next;lastMinute=minute;
        setContentDescription("时间 "+current);
        progress=roll?0:1;
        if(roll){
            animation=ValueAnimator.ofFloat(0,1);
            animation.setDuration(620);
            animation.setInterpolator(new PathInterpolator(.22f,1f,.36f,1f));
            animation.addUpdateListener(value->{progress=(float)value.getAnimatedValue();invalidate();});
            animation.start();
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        canvas.save();canvas.scale(getWidth()/designWidth,getHeight()/HEIGHT);
        canvas.clipRect(0,0,designWidth,HEIGHT);
        float x=0;
        for(int i=0;i<current.length();i++){
            boolean changed=progress<1&&previous.length()==5&&current.charAt(i)!=previous.charAt(i);
            if(changed){
                // A full glyph height of travel with a soft handoff, no layout or bitmap blur.
                paint.setAlpha(Math.round(255*(1-progress)));
                canvas.drawText(previous,i,i+1,x,BASELINE-HEIGHT*progress,paint);
                paint.setAlpha(Math.round(255*progress));
                canvas.drawText(current,i,i+1,x,BASELINE+HEIGHT*(1-progress),paint);
            }else{
                paint.setAlpha(255);canvas.drawText(current,i,i+1,x,BASELINE,paint);
            }
            x+=i==2?colonWidth:digitWidth;
        }
        paint.setAlpha(255);canvas.restore();
    }
}
