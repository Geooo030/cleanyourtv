package com.gathin.glasshome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** City-level weather only. No location permission, IP geolocation, or UI-thread networking. */
final class WeatherController {
    static final long REFRESH_MS=15*60*1000L,MAX_AGE_MS=6*60*60*1000L;
    private static final String ENDPOINT="https://api.open-meteo.com/v1/forecast?latitude=23.01797&longitude=113.74866&current=temperature_2m,weather_code,is_day&temperature_unit=celsius&timeformat=unixtime&timezone=Asia%2FShanghai&forecast_days=1";
    interface Listener {void show(Reading reading,boolean unavailable);}
    static final class Reading {
        final int temperature,code;final boolean day;final long observedAt,fetchedAt;
        Reading(int temperature,int code,boolean day,long observedAt,long fetchedAt){
            this.temperature=temperature;this.code=code;this.day=day;this.observedAt=observedAt;this.fetchedAt=fetchedAt;
        }
    }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final SharedPreferences preferences;
    private final Listener listener;
    private volatile HttpURLConnection connection;
    private Future<?> request;
    private Reading reading;
    private boolean running,unavailable;
    private int generation;
    private final Runnable refresh=()->fetch();
    private final Runnable ageTick=new Runnable(){@Override public void run(){if(running){publish();handler.postDelayed(this,60000);}}};

    WeatherController(Context context,Listener listener){
        this.listener=listener;
        preferences=context.getSharedPreferences("weather_dongguan_v1",Context.MODE_PRIVATE);
        try{reading=parse(preferences.getString("response",""),preferences.getLong("fetchedAt",0));}
        catch(Exception ignored){reading=null;}
    }
    void start(){
        stop();running=true;publish();handler.postDelayed(ageTick,60000);
        long delay=reading==null?0:Math.max(0,REFRESH_MS-(System.currentTimeMillis()-reading.fetchedAt));
        if(unavailable)delay=0;
        handler.postDelayed(refresh,delay);
    }
    void stop(){
        running=false;generation++;handler.removeCallbacksAndMessages(null);
        if(request!=null){request.cancel(true);request=null;}
        HttpURLConnection active=connection;if(active!=null)active.disconnect();
    }
    void close(){stop();executor.shutdownNow();}
    private void publish(){
        long now=System.currentTimeMillis();
        if(reading!=null&&(now-reading.observedAt>MAX_AGE_MS||reading.observedAt>now+30*60*1000L))reading=null;
        listener.show(reading,unavailable);
    }
    private void fetch(){
        if(!running)return;
        final int token=generation;
        request=executor.submit(()->{
            HttpURLConnection active=null;
            try{
                if(Thread.currentThread().isInterrupted())return;
                active=(HttpURLConnection)new URL(ENDPOINT).openConnection();connection=active;
                active.setConnectTimeout(10000);active.setReadTimeout(10000);
                active.setRequestProperty("Accept","application/json");
                active.setRequestProperty("User-Agent","GlassHome/1.6.6");
                if(active.getResponseCode()!=200)throw new java.io.IOException("Weather HTTP "+active.getResponseCode());
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                try(InputStream input=active.getInputStream()){
                    byte[] bytes=new byte[2048];int count;
                    while((count=input.read(bytes))!=-1){
                        if(Thread.currentThread().isInterrupted())return;
                        if(buffer.size()+count>65536)throw new java.io.IOException("Weather response too large");
                        buffer.write(bytes,0,count);
                    }
                }
                String body=buffer.toString("UTF-8");
                Reading result=parse(body,System.currentTimeMillis());
                handler.post(()->{
                    if(!running||token!=generation)return;
                    reading=result;unavailable=false;
                    preferences.edit().putString("response",body).putLong("fetchedAt",result.fetchedAt).apply();
                    publish();handler.postDelayed(refresh,REFRESH_MS);
                    Log.i("GlassWeather","Dongguan weather updated, observation="+result.observedAt);
                });
            }catch(Exception error){
                Log.w("GlassWeather","Weather update unavailable",error);
                handler.post(()->{
                    if(!running||token!=generation)return;
                    unavailable=true;publish();handler.postDelayed(refresh,5*60*1000L);
                });
            }finally{if(active!=null)active.disconnect();connection=null;}
        });
    }
    private static Reading parse(String body,long fetchedAt) throws Exception {
        JSONObject root=new JSONObject(body),current=root.getJSONObject("current");
        double temperature=current.getDouble("temperature_2m");
        long observedAt=current.getLong("time")*1000L,now=System.currentTimeMillis();
        int code=current.getInt("weather_code"),day=current.getInt("is_day");
        if(Double.isNaN(temperature)||Double.isInfinite(temperature)||temperature< -90||temperature>60
                ||code<0||code>99||(day!=0&&day!=1)||observedAt>now+30*60*1000L||now-observedAt>MAX_AGE_MS
                ||fetchedAt<=0||fetchedAt>now+5*60*1000L
                ||!"°C".equals(root.getJSONObject("current_units").getString("temperature_2m")))
            throw new java.io.IOException("Invalid or expired weather data");
        return new Reading((int)Math.round(temperature),code,day==1,observedAt,fetchedAt);
    }
}
