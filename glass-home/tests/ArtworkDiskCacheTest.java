package com.gathin.glasshome;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;

public final class ArtworkDiskCacheTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        File directory=Files.createTempDirectory("glass-artwork-test-").toFile();
        try{
            ArtworkDiskCache cache=new ArtworkDiskCache(directory,600);byte[] a=new byte[150];Arrays.fill(a,(byte)7);
            cache.write("account-a/cover/1",a,1000,1000);
            ArtworkDiskCache reopened=new ArtworkDiskCache(directory,600);
            ArtworkDiskCache.Entry entry=reopened.read("account-a/cover/1",2000);
            check(entry!=null&&Arrays.equals(entry.bytes,a),"encoded artwork survives process/store recreation");
            check(reopened.read("account-b/cover/1",2000)==null,"account scopes do not share keys");
            check(entry.fresh(2000),"recent cover is fresh");
            check(!reopened.read("account-a/cover/1",1000+ArtworkDiskCache.FRESH_FOR+1).fresh(1000+ArtworkDiskCache.FRESH_FOR+1),"LRU touch must not postpone refresh age");
            reopened.clear();reopened.write("one",a,1000,1000);reopened.write("two",a,1000,2000);reopened.write("three",a,1000,3000);reopened.read("one",4000);reopened.write("four",a,1000,5000);
            check(reopened.read("two",6000)==null,"least recently viewed cover is evicted");check(reopened.read("one",6000)!=null,"recently viewed cover survives eviction");
            reopened.write("one",new byte[]{1,2,3},7000,7000);check(reopened.read("one",7000).bytes.length==3,"atomic replacement preserves new payload");
            for(File file:directory.listFiles())if(file.getName().endsWith(".art")){try(FileOutputStream out=new FileOutputStream(file)){out.write(1);}break;}
            boolean rejected=false;for(String key:new String[]{"one","three","four"})if(reopened.read(key,8000)==null)rejected=true;
            check(rejected,"corrupt entries are rejected");reopened.clear();check(directory.listFiles().length==0,"logout removes artwork");
            System.out.println("PASS: persistent artwork, account isolation, freshness, LRU bound, atomic replacement, corrupt cache, logout");
        }finally{for(File file:directory.listFiles())file.delete();directory.delete();}
    }
}
