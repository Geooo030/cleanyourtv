package com.gathin.glasshome;
public class PlaybackBookmarkTest {
    private static void equal(int expected,int actual){if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args){
        PlaybackBookmark b=new PlaybackBookmark(7,71345);equal(71345,b.forTrack(7,180000));equal(0,b.forTrack(8,180000));
        b.record(7,90000,180000);PlaybackBookmark loaded=new PlaybackBookmark(b.trackId,b.position);equal(90000,loaded.forTrack(7,180000));
        b.record(8,1234,200000);equal(0,b.forTrack(7,180000));equal(1234,b.forTrack(8,200000));
        b.record(8,199900,200000);equal(0,b.forTrack(8,200000));b.record(8,-1,200000);equal(0,b.forTrack(8,200000));
        b.clear();equal(0,b.forTrack(8,200000));System.out.println("PASS: checkpoint reload, song identity, completion, negative position, logout");
    }
}
