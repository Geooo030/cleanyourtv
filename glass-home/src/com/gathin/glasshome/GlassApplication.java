package com.gathin.glasshome;

import android.app.Application;

public final class GlassApplication extends Application {
    @Override public void onCreate(){super.onCreate();HomeRouting.register(this);}
}
