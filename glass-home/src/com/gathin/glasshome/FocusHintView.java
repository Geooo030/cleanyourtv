package com.gathin.glasshome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/** One quiet, screen-anchored hint. Only this small text view redraws during the sweep. */
final class FocusHintView extends View {
    private static final PathInterpolator EASE=new PathInterpolator(.2f,0,.2f,1);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix sweepMatrix=new Matrix();
    private final float unit;
    private final LinearGradient sweep;
    private String message="",requested="";
    private float reveal=1,phase;
    private ValueAnimator transition,pulse;
    private boolean released;

    static FocusHintView attach(GlassHomeActivity owner,FrameLayout root,int sw,int sh){
        float scale=Math.min(sw/1920f,sh/1080f);
        FocusHintView view=new FocusHintView(owner,scale);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(880*scale),Math.round(52*scale));
        p.leftMargin=Math.round((sw-1920*scale)/2+570*scale);
        p.topMargin=Math.round((sh-1080*scale)/2+990*scale);
        root.addView(view,p);return view;
    }
    private FocusHintView(GlassHomeActivity owner,float unit){
        super(owner);this.unit=unit;
        setFocusable(false);setClickable(false);
        paint.setTypeface(GlassFonts.create(owner,400));paint.setTextSize(20*unit);paint.setTextAlign(Paint.Align.CENTER);
        sweep=new LinearGradient(-90*unit,0,90*unit,0,new int[]{0x00ffffff,0xffffffff,0x00ffffff},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
    }
    void showHint(String value){
        if(released||value.equals(requested))return;
        requested=value;setContentDescription(value);cancelTransition();
        if(!isAttachedToWindow()||!isShown()||!ValueAnimator.areAnimatorsEnabled()){
            message=value;reveal=1;phase=0;invalidate();syncPulse();return;
        }
        if(message.isEmpty()){message=value;reveal=0;restartPulse();fadeTo(1,180,null);}
        else fadeTo(0,90,()->{message=requested;restartPulse();fadeTo(1,180,null);});
    }
    private void fadeTo(float target,long duration,Runnable then){
        transition=ValueAnimator.ofFloat(reveal,target);transition.setDuration(duration);transition.setInterpolator(EASE);
        transition.addUpdateListener(a->{reveal=(float)a.getAnimatedValue();invalidate();});
        transition.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){transition=null;if(then!=null)then.run();}});
        transition.start();
    }
    private void cancelTransition(){if(transition!=null){transition.removeAllListeners();transition.removeAllUpdateListeners();transition.cancel();transition=null;}}
    private void stopPulse(){if(pulse!=null){pulse.removeAllUpdateListeners();pulse.cancel();pulse=null;}}
    private void restartPulse(){stopPulse();phase=0;syncPulse();}
    private void syncPulse(){
        boolean active=!released&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE&&hasWindowFocus()&&!message.isEmpty()&&ValueAnimator.areAnimatorsEnabled();
        if(!active){stopPulse();return;}
        if(pulse!=null)return;
        pulse=ValueAnimator.ofFloat(0,1);pulse.setDuration(2600);pulse.setInterpolator(new LinearInterpolator());pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});pulse.start();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(message.isEmpty()||reveal<=0)return;
        paint.setTextSize(20*unit);
        float width=paint.measureText(message),limit=getWidth()-24*unit;
        if(width>limit){paint.setTextSize(20*unit*limit/width);width=limit;}
        float baseline=(getHeight()-paint.ascent()-paint.descent())/2;
        float breath=(float)(.5-.5*Math.cos(phase*Math.PI*2));
        paint.setShader(null);paint.setColor(Color.WHITE);paint.setAlpha(Math.round((150+42*breath)*reveal));
        paint.setShadowLayer(3*unit,0,unit,0x55000000);canvas.drawText(message,getWidth()/2f,baseline,paint);paint.clearShadowLayer();
        if(ValueAnimator.areAnimatorsEnabled()&&phase<.78f){
            float travel=Math.min(1,phase/.78f);
            sweepMatrix.setTranslate(getWidth()/2f-width/2-100*unit+(width+200*unit)*travel,0);sweep.setLocalMatrix(sweepMatrix);
            paint.setShader(sweep);paint.setAlpha(Math.round(215*reveal));canvas.drawText(message,getWidth()/2f,baseline,paint);paint.setShader(null);
        }
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();syncPulse();}
    @Override protected void onDetachedFromWindow(){cancelTransition();stopPulse();super.onDetachedFromWindow();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);syncPulse();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);syncPulse();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);syncPulse();}
    void release(){released=true;cancelTransition();stopPulse();}
}
