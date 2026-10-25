package com.gathin.glasshome;

import android.app.Dialog;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;

/** Shared light glass window; only its panel transforms, never the live video hierarchy. */
abstract class OptionsOverlay extends Dialog {
    final GlassHomeActivity owner;
    final int focusReturn;
    final boolean hideHome;
    final float x,y,w,h;
    FrameLayout panel;
    private FocusHintView focusHint;
    float unit,expansion;
    private boolean closing,disposed;
    private ValueAnimator motion;
    Runnable afterClose;
    final Typeface regular,medium,light;

    OptionsOverlay(GlassHomeActivity owner,int focusReturn,boolean hideHome,float x,float y,float w,float h){
        super(owner);this.owner=owner;this.focusReturn=focusReturn;this.hideHome=hideHome;
        this.x=x;this.y=y;this.w=w;this.h=h;
        regular=GlassFonts.create(owner,400);medium=GlassFonts.create(owner,500);light=GlassFonts.create(owner,300);
        setOnDismissListener(d->{
            disposed=true;if(focusHint!=null)focusHint.release();cancelMotion();owner.setAppsMotion(false);if(hideHome)owner.setAppsExpansion(0);
            owner.optionsClosed(this);onClosed();Runnable next=afterClose;afterClose=null;if(next!=null)next.run();
        });
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
        int sw=owner.getWindow().getDecorView().getWidth(),sh=owner.getWindow().getDecorView().getHeight();
        unit=Math.min(sw/1920f,sh/1080f);
        FrameLayout root=new FrameLayout(owner);root.setOnClickListener(v->{if(expansion>=.999f)dismiss();});
        panel=new FrameLayout(owner);panel.setOnClickListener(v->{});
        float left=(sw-w*unit)/2f+(x-((1920-w)/2))*unit,top=(sh-h*unit)/2f+(y-((1080-h)/2))*unit;
        panel.setBackground(new GlassPanelDrawable(unit,sw,sh,owner.scene).at(left,top));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(px(w),px(h));p.leftMargin=Math.round(left);p.topMargin=Math.round(top);root.addView(panel,p);
        focusHint=FocusHintView.attach(owner,root,sw,sh);
        hint("方向键选择 · 确认打开 · 返回关闭");
        buildPanel();setContentView(root);
        Window window=getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(hideHome?.08f:.04f);
        window.setLayout(-1,-1);window.setWindowAnimations(0);
        window.getDecorView().setSystemUiVisibility(owner.getWindow().getDecorView().getSystemUiVisibility());
        panel.setPivotX(px(motionPivotX()));panel.setPivotY(hideHome?px(h):0);
        apply(0);panel.post(()->{if(!closing&&!disposed)animate(true);});
    }
    void hint(String value){if(focusHint!=null)focusHint.showHint(value);}
    abstract void buildPanel();
    float motionPivotX(){return w*.65f;}
    float collapsedScale(){return .96f;}
    float collapsedOffset(){return hideHome?16:-10;}
    long openingDuration(){return 300;}
    long closingDuration(){return 190;}
    int restingLayer(){return View.LAYER_TYPE_NONE;}
    android.animation.TimeInterpolator panelEasing(){return new PathInterpolator(.2f,0,.1f,1);}
    void onClosed(){}
    void updateBackdrop(){if(!disposed&&panel!=null)panel.getBackground().invalidateSelf();}
    boolean settleOpening(){
        if(closing||disposed)return false;
        cancelMotion();panel.setLayerType(restingLayer(),null);apply(1);owner.setAppsMotion(false);return true;
    }
    int px(float v){return Math.round(unit*v);}
    FrameLayout.LayoutParams box(float x,float y,float w,float h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(px(w),px(h));p.leftMargin=px(x);p.topMargin=px(y);return p;}
    TextView label(String value,float size,int color,Typeface font){TextView t=new TextView(owner);t.setText(value);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));t.setTextColor(color);t.setTypeface(font);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);t.setShadowLayer(px(3),0,px(1),0x44000000);return t;}
    static GradientDrawable surface(int color,int stroke,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);d.setStroke(1,stroke);return d;}
    TextView closeButton(){TextView v=label("×",36,Color.WHITE,regular);v.setGravity(Gravity.CENTER);v.setFocusable(true);v.setClickable(true);v.setContentDescription("关闭面板");v.setId(View.generateViewId());v.setBackground(surface(0x0effffff,0x25ffffff,px(26)));v.setOnFocusChangeListener((a,f)->{v.setBackground(surface(f?0x66ffffff:0x0effffff,0x55ffffff,px(26)));if(f)hint("关闭面板 · 确认或返回关闭");});v.setOnClickListener(a->dismiss());panel.addView(v,box(w-100,40,52,52));return v;}
    private void apply(float amount){expansion=amount;panel.setAlpha(amount);float start=collapsedScale();panel.setScaleX(start+(1-start)*amount);panel.setScaleY(start+(1-start)*amount);panel.setTranslationY(px(collapsedOffset())*(1-amount));if(hideHome)owner.setAppsExpansion(amount);}
    private void cancelMotion(){if(motion!=null){motion.removeAllListeners();motion.cancel();}}
    private void animate(boolean opening){
        cancelMotion();owner.setAppsMotion(true);
        if(!ValueAnimator.areAnimatorsEnabled()){apply(opening?1:0);owner.setAppsMotion(false);if(!opening)super.dismiss();return;}
        panel.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        motion=ValueAnimator.ofFloat(expansion,opening?1:0);motion.setDuration(opening?openingDuration():closingDuration());motion.setInterpolator(panelEasing());
        motion.addUpdateListener(a->apply((float)a.getAnimatedValue()));
        motion.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){panel.setLayerType(restingLayer(),null);owner.setAppsMotion(false);if(!opening)OptionsOverlay.super.dismiss();}});motion.start();
    }
    @Override public void dismiss(){if(closing||disposed)return;closing=true;if(panel==null)super.dismiss();else animate(false);}
    void dismissImmediately(){if(disposed)return;closing=true;afterClose=null;cancelMotion();super.dismiss();}
    @Override public void onBackPressed(){dismiss();}
}
