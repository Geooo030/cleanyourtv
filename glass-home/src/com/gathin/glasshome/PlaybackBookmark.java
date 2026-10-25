package com.gathin.glasshome;

/** A checkpoint belongs to one song; a completed song resumes from its beginning. */
final class PlaybackBookmark {
    long trackId;int position;
    PlaybackBookmark(long id,int ms){trackId=id;position=Math.max(0,ms);}
    int forTrack(long id,int duration){if(id!=trackId)return 0;return normalize(position,duration);}
    void record(long id,int ms,int duration){if(id<=0)return;trackId=id;position=normalize(ms,duration);}
    private static int normalize(int ms,int duration){int value=Math.max(0,ms);return duration>0&&value>=Math.max(0,duration-700)?0:value;}
    void clear(){trackId=0;position=0;}
}
