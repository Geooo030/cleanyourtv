package com.gathin.glasshome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontStyle;
import android.os.Build;
import android.util.Log;

/** Inter Latin + Source Han Sans CN. Real bundled fonts and real weight selection, with system fallback. */
final class GlassFonts {
    static Typeface create(Context context,int weight) {
        if(Build.VERSION.SDK_INT>=29) {
            try {
                Font latin=new Font.Builder(context.getResources(),R.font.inter)
                        .setFontVariationSettings("'wght' "+weight+", 'opsz' 28").setWeight(weight).build();
                Font chinese=new Font.Builder(context.getResources(),R.font.source_han_sans_cn)
                        .setFontVariationSettings("'wght' "+weight).setWeight(weight).build();
                Typeface result=new Typeface.CustomFallbackBuilder(new FontFamily.Builder(latin).build())
                        .addCustomFallback(new FontFamily.Builder(chinese).build())
                        .setStyle(new FontStyle(weight,FontStyle.FONT_SLANT_UPRIGHT)).setSystemFallback("sans-serif").build();
                Log.i("GlassFonts","Loaded Inter + Source Han Sans CN, weight="+weight);
                return result;
            }catch(Exception error) { Log.w("GlassFonts","Bundled font loading failed",error); }
        }
        if(Build.VERSION.SDK_INT>=28)return Typeface.create(Typeface.SANS_SERIF,weight,false);
        return Typeface.create(weight<=300?"sans-serif-light":"sans-serif",Typeface.NORMAL);
    }
    private GlassFonts() { }
}
