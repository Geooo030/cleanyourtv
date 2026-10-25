package com.gathin.glasshome;

import android.graphics.*;

/** Resolution-independent Music glyph, painted once into the home card's cached layer. */
final class MusicArtwork {
    static void draw(Canvas canvas,float x,float y,float size){
        canvas.save();canvas.translate(x,y);canvas.scale(size/100,size/100);
        Paint p=new Paint(3);p.setShader(new LinearGradient(0,0,100,100,0xffff6079,0xffeb1647,Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0,0,100,100,23,23,p);p.setShader(null);p.setColor(Color.WHITE);
        Path note=new Path();note.moveTo(38,28);note.lineTo(76,20);note.lineTo(76,68);note.cubicTo(76,79,55,84,55,72);note.cubicTo(55,64,65,62,70,63);note.lineTo(70,37);note.lineTo(44,43);note.lineTo(44,75);note.cubicTo(44,87,22,91,22,79);note.cubicTo(22,71,32,69,38,70);note.close();canvas.drawPath(note,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x30ffffff);canvas.drawRoundRect(.5f,.5f,99.5f,99.5f,23,23,p);canvas.restore();
    }
}
