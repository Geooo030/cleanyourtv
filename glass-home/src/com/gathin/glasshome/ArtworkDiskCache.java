package com.gathin.glasshome;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Private, bounded encoded artwork. Fetch age is independent of LRU access time. */
final class ArtworkDiskCache {
    private static final int MAGIC=0x47484131,HEADER=16;
    static final int MAX_ENTRY=2*1024*1024;
    static final long FRESH_FOR=7L*24*60*60*1000;
    static final class Entry {final byte[] bytes;final long fetchedAt;Entry(byte[] bytes,long fetchedAt){this.bytes=bytes;this.fetchedAt=fetchedAt;}boolean fresh(long now){return fetchedAt<=now&&now-fetchedAt<FRESH_FOR;}}
    private final File directory;private final long budget;
    ArtworkDiskCache(File directory,long budget){this.directory=directory;this.budget=budget;directory.mkdirs();}
    private File file(String key){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));StringBuilder name=new StringBuilder();for(byte b:digest)name.append(String.format(Locale.US,"%02x",b&255));return new File(directory,name+".art");}catch(Exception e){throw new IllegalStateException(e);}}
    synchronized Entry read(String key,long now){
        File file=file(key);if(!file.isFile())return null;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))){
            if(in.readInt()!=MAGIC)throw new IOException();long fetched=in.readLong();int length=in.readInt();
            if(fetched<=0||length<=0||length>MAX_ENTRY||file.length()!=HEADER+(long)length)throw new IOException();
            byte[] bytes=new byte[length];in.readFully(bytes);file.setLastModified(now);return new Entry(bytes,fetched);
        }catch(IOException bad){file.delete();return null;}
    }
    synchronized void write(String key,byte[] bytes,long fetchedAt,long now){
        if(bytes==null||bytes.length==0||bytes.length>MAX_ENTRY||bytes.length+HEADER>budget||fetchedAt<=0)return;
        directory.mkdirs();File temp=null;
        try{
            temp=File.createTempFile("write-",".tmp",directory);
            try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))){out.writeInt(MAGIC);out.writeLong(fetchedAt);out.writeInt(bytes.length);out.write(bytes);}
            File destination=file(key);
            try{Files.move(temp.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            destination.setLastModified(now);trim();
        }catch(IOException ignored){}finally{if(temp!=null)temp.delete();}
    }
    synchronized void remove(String key){file(key).delete();}
    synchronized void clear(){File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.isFile()&&(f.getName().endsWith(".art")||f.getName().startsWith("write-")&&f.getName().endsWith(".tmp")))f.delete();}
    private void trim(){File[] files=directory.listFiles((dir,name)->name.endsWith(".art"));if(files==null)return;long total=0;for(File f:files)total+=f.length();Arrays.sort(files,Comparator.comparingLong(File::lastModified).thenComparing(File::getName));for(File f:files){if(total<=budget)break;long size=f.length();if(f.delete())total-=size;}}
}
