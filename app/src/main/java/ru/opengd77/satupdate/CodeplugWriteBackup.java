package ru.opengd77.satupdate;

import android.content.Context;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Durable recovery evidence; never automatically replays writes after a failed session. */
final class CodeplugWriteBackup {
    static File marker(Context c){return new File(c.getFilesDir(),"codeplug-write-pending");}
    static boolean pending(Context c){return marker(c).exists();}
    static void clear(Context c)throws IOException{
        if(marker(c).exists()&&!marker(c).delete())throw new IOException("Не удалось завершить журнал записи");
    }
    static File latest(Context c){
        File[] files=new File(c.getFilesDir(),"codeplug-backups").listFiles((d,n)->n.endsWith(".zip"));
        if(files==null||files.length==0)return null;
        Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));return files[files.length-1];
    }
    static void save(Context c,CodeplugWritePlan plan,List<CodeplugWritePlan.Sector> sectors)throws IOException{
        File dir=new File(c.getFilesDir(),"codeplug-backups");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Не удалось создать папку резервных копий");
        File file=new File(dir,"MD9600-"+System.currentTimeMillis()+".zip");
        try(FileOutputStream out=new FileOutputStream(file);ZipOutputStream zip=new ZipOutputStream(out)){
            entry(zip,"before.ogcproj",plan.project.encode());
            entry(zip,"after.ogcproj",plan.completedProject().encode());
            entry(zip,"README.txt",("MD-9600 codeplug write backup\n"+plan.summary()
                +"\nFLASH-before/ = full original 4096-byte sectors\nFLASH-after/ = intended sectors\n"
                +"This backup is NOT a Windows CPS .ogd file. Do not flash it as firmware.\n").getBytes("UTF-8"));
            for(CodeplugWritePlan.Sector s:sectors){
                String name=String.format(Locale.US,"%08x.bin",s.address);
                entry(zip,"FLASH-before/"+name,s.before);entry(zip,"FLASH-after/"+name,s.after);
            }
            zip.finish();zip.flush();out.getFD().sync();
        }catch(IOException e){file.delete();throw e;}
        try(FileOutputStream out=new FileOutputStream(marker(c))){out.write(file.getName().getBytes("UTF-8"));out.getFD().sync();}
    }
    private static void entry(ZipOutputStream out,String name,byte[] bytes)throws IOException{
        out.putNextEntry(new ZipEntry(name));out.write(bytes);out.closeEntry();
    }
}
