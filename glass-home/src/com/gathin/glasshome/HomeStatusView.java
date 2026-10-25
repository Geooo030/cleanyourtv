package com.gathin.glasshome;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Places two small independently rendered views on the scene's 1920 x 1080 canvas. */
final class HomeStatusView extends ViewGroup {
    private final RollingClockView clock;
    private final WeatherSummaryView weather;
    private final WeatherController controller;
    private float scale,offsetX,offsetY;
    HomeStatusView(Context context){
        super(context);setFocusable(false);setClipChildren(false);
        clock=new RollingClockView(context);weather=new WeatherSummaryView(context);
        addView(clock);addView(weather);controller=new WeatherController(context,weather::show);
    }
    void start(){clock.start();controller.start();}
    void stop(){clock.stop();controller.stop();}
    void close(){clock.stop();controller.close();}
    @Override protected void onMeasure(int width,int height){
        int w=MeasureSpec.getSize(width),h=MeasureSpec.getSize(height);setMeasuredDimension(w,h);
        scale=Math.min(w/1920f,h/1080f);offsetX=(w-1920*scale)/2;offsetY=(h-1080*scale)/2;
        measure(clock,clock.designWidth(),RollingClockView.HEIGHT);measure(weather,WeatherSummaryView.WIDTH,WeatherSummaryView.HEIGHT);
    }
    private void measure(View view,float width,float height){view.measure(MeasureSpec.makeMeasureSpec(Math.round(width*scale),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(height*scale),MeasureSpec.EXACTLY));}
    private void place(View view,float x,float y){int left=Math.round(offsetX+x*scale),top=Math.round(offsetY+y*scale);view.layout(left,top,left+view.getMeasuredWidth(),top+view.getMeasuredHeight());}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        place(clock,125,83);place(weather,125+clock.designWidth()+70,82);
    }
}
