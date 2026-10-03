package ru.opengd77.satupdate;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Reads codeplug backups as project data. Never replays FLASH sectors or clears pending writes. */
final class ProjectRecovery {
    final CodeplugProject before,after;
    private ProjectRecovery(CodeplugProject before,CodeplugProject after)throws IOException{
        this.before=before;this.after=after;
        if(!sameRadio(before.identity,after.identity)||!CodeplugProject.equal(before.working,after.working))throw new IOException("Несогласованные проекты в резервной копии");
        byte[][] original=CodeplugProject.blocks(before.original),written=CodeplugProject.blocks(after.original),working=CodeplugProject.blocks(before.working);int count=0;
        for(int block=0;block<original.length;block++)for(int i=0;i<original[block].length;i++)if(original[block][i]!=written[block][i]){
            if(written[block][i]!=working[block][i])throw new IOException("Резервная копия содержит посторонние изменения");count++;
        }
        if(count==0)throw new IOException("В резервной копии нет записанных изменений");
        try{CodeplugIntegrity.masks(before);CodeplugIntegrity.links(before.original,after.original);}catch(IllegalArgumentException e){throw new IOException("Неверные данные резервной копии: "+e.getMessage(),e);}
    }
    static boolean sameRadio(RadioDriver.Identity a,RadioDriver.Identity b){return a!=null&&b!=null&&a.radioType==b.radioType&&a.infoVersion==b.infoVersion&&a.model.equals(b.model)&&a.firmware.equals(b.firmware);}
    static ProjectRecovery read(InputStream input)throws IOException{
        if(input==null)throw new IOException("Файл резервной копии не открыт");CodeplugProject before=null,after=null;int total=0,entries=0;
        try(ZipInputStream zip=new ZipInputStream(input)){
            ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null){
                if(++entries>512)throw new IOException("Слишком много файлов в резервной копии");
                boolean project=entry.getName().equals("before.ogcproj")||entry.getName().equals("after.ogcproj");
                ByteArrayOutputStream data=project?new ByteArrayOutputStream():null;int n;
                while((n=zip.read(buffer))!=-1){total+=n;if(total>16*1024*1024)throw new IOException("Резервная копия слишком большая");
                    if(project){if(data.size()+n>400000)throw new IOException("Проект в резервной копии слишком большой");data.write(buffer,0,n);}}
                if(project){CodeplugProject p=CodeplugProject.read(new ByteArrayInputStream(data.toByteArray()));
                    if(entry.getName().equals("before.ogcproj")){if(before!=null)throw new IOException("Повторный before.ogcproj");before=p;}
                    else{if(after!=null)throw new IOException("Повторный after.ogcproj");after=p;}}
                zip.closeEntry();
            }
        }
        if(before==null||after==null)throw new IOException("Нужна ZIP-копия записи codeplug с before.ogcproj и after.ogcproj");
        return new ProjectRecovery(before,after);
    }
    CodeplugProject restore(CodeplugProject current){
        if(!sameRadio(current.identity,before.identity))throw new IllegalArgumentException("Модель, формат или прошивка резервной копии отличаются от текущего проекта");
        // Recover only bytes that this write actually touched. Keep unrelated and unsent edits.
        CodeplugProject result=current.edit(image->{
            byte[][] target=CodeplugProject.blocks(image),a=CodeplugProject.blocks(before.original),b=CodeplugProject.blocks(after.original);
            for(int block=0;block<a.length;block++)for(int i=0;i<a[block].length;i++)if(a[block][i]!=b[block][i])target[block][i]=a[block][i];
        });
        CodeplugIntegrity.masks(result);CodeplugIntegrity.links(result.original,result.working);return result;
    }
}
