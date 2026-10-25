package com.gathin.glasshome;
import android.graphics.*;
import android.view.View;

/** White rounded waveform bars driven by the decoded song's 20 Hz energy envelope. */
final class MusicBeatView extends View {
    private final GlassHomeActivity owner;
    private final Paint paint=new Paint(3);
    private final boolean homeOnly;
    private static final float[] PROFILE={.58f,.86f,1f,.82f,.54f};
    MusicBeatView(GlassHomeActivity owner){this(owner,true);}
    MusicBeatView(GlassHomeActivity owner,boolean homeOnly){super(owner);this.owner=owner;this.homeOnly=homeOnly;paint.setColor(Color.WHITE);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){
        if(!owner.music.playing)return;float w=getWidth(),h=getHeight(),step=w*.17f,barWidth=w*.09f;
        float left=(w-(step*4+barWidth))/2f;
        for(int i=0;i<PROFILE.length;i++){
            float energy=.7f*owner.music.level(i)+.3f*owner.music.level(i+1);
            float height=h*(.12f+.76f*energy*PROFILE[i]);
            float x=left+i*step;
            c.drawRoundRect(x,(h-height)/2f,x+barWidth,(h+height)/2f,barWidth/2f,barWidth/2f,paint);
        }
        if((!homeOnly||owner.scene.getAlpha()>.01f)&&isShown())postInvalidateOnAnimation();
    }
}
