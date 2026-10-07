package ru.opengd77.satupdate;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Bounded durable project history. Complete checksum-protected revisions, not undo metadata. */
final class ProjectVersions {
    static final int KEEP=30;
    static File[] list(File directory){
        File[] files=directory.listFiles((dir,name)->name.endsWith(".ogcproj"));if(files==null)return new File[0];
        Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));return files;
    }
    static synchronized File save(File directory,byte[] data)throws IOException{
        // Validate before persisting a history revision.
        CodeplugProject.read(new ByteArrayInputStream(data));
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Не удалось создать историю проекта");
        String hash=hash(data);File[] existing=list(directory);
        if(existing.length>0&&existing[0].getName().endsWith("-"+hash+".ogcproj"))return existing[0];
        long stamp=System.currentTimeMillis();if(existing.length>0){try{stamp=Math.max(stamp,Long.parseLong(existing[0].getName().substring(0,13))+1);}catch(RuntimeException ignored){}}
        File target=new File(directory,String.format(Locale.US,"%013d-%s.ogcproj",stamp,hash));
        File temp=File.createTempFile("revision-",".tmp",directory);
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(data);out.getFD().sync();}
            if(!temp.renameTo(target))throw new IOException("Не удалось сохранить версию проекта");
        }finally{if(temp.exists())temp.delete();}
        File[] all=list(directory);for(int i=KEEP;i<all.length;i++)if(!all[i].delete())throw new IOException("Не удалось очистить старую версию проекта");
        return target;
    }
    private static String hash(byte[] bytes)throws IOException{
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder result=new StringBuilder();for(int i=0;i<8;i++)result.append(String.format(Locale.US,"%02x",digest[i]&255));return result.toString();}
        catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
}
