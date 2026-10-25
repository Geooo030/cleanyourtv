package com.gathin.glasshome;
import android.graphics.*;
import android.graphics.drawable.Drawable;

final class GlassPanelDrawable extends Drawable {
        private final float scale;
        private final int screenW,screenH;
        private final GlassScene scene;
        private float originX=Float.NaN,originY=Float.NaN;
        private int blurAlpha=121,tint=0x090b1730;
        private boolean controlCenterStyle;
        private float corner=38;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        GlassPanelDrawable(float scale,int screenW,int screenH,GlassScene scene){this.scale=scale;this.screenW=screenW;this.screenH=screenH;this.scene=scene;}
        GlassPanelDrawable at(float x,float y){originX=x;originY=y;return this;}
        GlassPanelDrawable controlCenter(){controlCenterStyle=true;blurAlpha=112;tint=0x0c121c2b;corner=48;return this;}
        GlassPanelDrawable preview(){blurAlpha=78;tint=0x0a111b2c;corner=34;return this;}
        GlassPanelDrawable circular(){controlCenterStyle=true;blurAlpha=128;tint=0x12111b29;corner=168;return this;}
        @Override public void draw(Canvas canvas){
            RectF bounds=new RectF(getBounds());float radius=corner*scale;
            canvas.save();Path clip=new Path();clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);canvas.clipPath(clip);
            float left=Float.isNaN(originX)?(screenW-bounds.width())/2:originX,top=Float.isNaN(originY)?(screenH-bounds.height())/2:originY;
            paint.setAlpha(blurAlpha);canvas.drawBitmap(scene.glassFrame(),null,new RectF(-left,-top,screenW-left,screenH-top),paint);paint.setAlpha(255);
            paint.setColor(tint);canvas.drawRect(bounds,paint);
            if(controlCenterStyle)paint.setAlpha(255);
            paint.setShader(new LinearGradient(0,0,bounds.width(),bounds.height(),new int[]{0x1cffffff,0x0affffff,0x081a2846},new float[]{0,.55f,1},Shader.TileMode.CLAMP));canvas.drawRect(bounds,paint);paint.setShader(null);canvas.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.4f*scale);
            paint.setShader(new LinearGradient(0,0,bounds.width(),bounds.height(),new int[]{0xa6ffffff,0x22ffffff,0x65ffffff},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(bounds,radius,radius,paint);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0,0,bounds.width(),0,new int[]{0x00ffffff,0x8cffffff,0x00ffffff},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(bounds.left+radius,bounds.top+1,bounds.right-radius,bounds.top+2*scale,scale,scale,paint);paint.setShader(null);
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(android.graphics.ColorFilter filter){}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
