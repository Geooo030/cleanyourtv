package com.gathin.glasshome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Quiet, unboxed weather beside the home clock. Icons are resolution-independent paths. */
final class WeatherSummaryView extends View {
    static final float WIDTH=460,HEIGHT=80;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface regular,medium;
    private WeatherController.Reading reading;
    private String condition="正在更新",temperature="—°",updated="";
    WeatherSummaryView(Context context){
        super(context);regular=GlassFonts.create(context,400);medium=GlassFonts.create(context,500);
        setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void show(WeatherController.Reading value,boolean unavailable){
        reading=value;updated="";
        if(value==null){temperature="—°";condition=unavailable?"天气暂不可用":"正在更新";}
        else{
            temperature=value.temperature+"°";condition=condition(value.code);
            if(unavailable||System.currentTimeMillis()-value.observedAt>60*60*1000L){
                SimpleDateFormat format=new SimpleDateFormat("HH:mm",Locale.CHINA);
                format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
                updated=format.format(new Date(value.observedAt))+" 更新";
            }
        }
        setContentDescription("东莞，"+(value==null?condition:value.temperature+"摄氏度，"+condition)
                +(updated.isEmpty()?"":"，上次 "+updated)+"，天气数据 Open-Meteo");
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/WIDTH,getHeight()/HEIGHT);
        // Location marker, kept small so the temperature remains the secondary focal point.
        stroke(0xcfffffff,1.7f);
        Path pin=new Path();pin.moveTo(9,25);pin.cubicTo(6,21,2,17,2,13);
        pin.cubicTo(2,3,16,3,16,13);pin.cubicTo(16,17,12,21,9,25);
        canvas.drawPath(pin,paint);canvas.drawCircle(9,12,2.4f,paint);
        text(canvas,"东莞",29,24,20,0xe6ffffff,medium);
        text(canvas,updated.isEmpty()?"Open-Meteo":"Open-Meteo · "+updated,100,23,12,0x91ffffff,regular);
        if(reading!=null){canvas.save();canvas.translate(-2,34);canvas.scale(.8f,.8f);weatherIcon(canvas,reading.code,reading.day);canvas.restore();}
        else{stroke(0x99ffffff,2);canvas.drawLine(7,55,27,55,paint);}
        text(canvas,temperature,47,69,36,Color.WHITE,medium);
        float labelX=47+paint.measureText(temperature)+16;
        text(canvas,condition,labelX,67,20,0xdeffffff,regular);
        canvas.restore();
    }
    private void text(Canvas c,String value,float x,float y,float size,int color,Typeface font){
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(size);paint.setTypeface(font);
        c.drawText(value,x,y,paint);
    }
    private void stroke(int color,float width){paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    private void weatherIcon(Canvas c,int code,boolean day){
        stroke(0xf2ffffff,2.3f);
        if(code==0){if(day)sun(c,24,24,9);else moon(c);return;}
        if(code==45||code==48){for(int i=0;i<3;i++)c.drawLine(i==1?5:10,15+i*9,i==1?43:38,15+i*9,paint);return;}
        if(code==1||code==2){sun(c,15,14,6);cloud(c);return;}
        if(code>=95){cloud(c);Path bolt=new Path();bolt.moveTo(25,32);bolt.lineTo(20,40);bolt.lineTo(26,40);bolt.lineTo(22,47);c.drawPath(bolt,paint);return;}
        if((code>=71&&code<=77)||code==85||code==86){cloud(c);for(int x=15;x<=35;x+=10){c.drawLine(x,36,x,44,paint);c.drawLine(x-3,38,x+3,42,paint);c.drawLine(x-3,42,x+3,38,paint);}return;}
        if((code>=51&&code<=67)||(code>=80&&code<=82)){cloud(c);for(int x=15;x<=35;x+=10)c.drawLine(x,36,x-3,43,paint);return;}
        if(code==3){cloud(c);return;}
        c.drawCircle(24,24,14,paint);c.drawLine(17,24,31,24,paint);
    }
    private void sun(Canvas c,float x,float y,float radius){
        c.drawCircle(x,y,radius,paint);
        for(int i=0;i<8;i++){double a=i*Math.PI/4;float dx=(float)Math.cos(a),dy=(float)Math.sin(a);c.drawLine(x+dx*(radius+4),y+dy*(radius+4),x+dx*(radius+8),y+dy*(radius+8),paint);}
    }
    private void moon(Canvas c){
        Path moon=new Path();moon.moveTo(27,6);moon.cubicTo(8,5,3,29,18,39);
        moon.cubicTo(29,46,41,39,43,29);moon.cubicTo(27,33,18,18,27,6);c.drawPath(moon,paint);
    }
    private void cloud(Canvas c){
        Path cloud=new Path();cloud.moveTo(12,32);cloud.cubicTo(0,32,2,17,13,18);
        cloud.cubicTo(14,5,34,6,35,20);cloud.cubicTo(47,17,50,32,39,32);cloud.close();c.drawPath(cloud,paint);
    }
    private static String condition(int code){
        switch(code){
            case 0:return "晴";case 1:return "晴间多云";case 2:return "多云";case 3:return "阴";
            case 45:return "雾";case 48:return "雾凇";
            case 51:case 53:case 55:return "毛毛雨";case 56:case 57:return "冻毛毛雨";
            case 61:return "小雨";case 63:return "中雨";case 65:return "大雨";case 66:case 67:return "冻雨";
            case 71:return "小雪";case 73:return "中雪";case 75:return "大雪";case 77:return "雪粒";
            case 80:case 81:case 82:return "阵雨";case 85:case 86:return "阵雪";
            case 95:return "雷雨";case 96:case 99:return "雷雨伴冰雹";
            default:return "天气信息";
        }
    }
}
