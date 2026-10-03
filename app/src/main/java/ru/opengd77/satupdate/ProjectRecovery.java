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
        // Restore whole affected records/fields, preventing hybrid multibyte values.
        // Conflicting later edits in an affected record are rejected rather than overwritten.
        CodeplugProject result=current.edit(image->{
            byte[][] target=CodeplugProject.blocks(image),a=CodeplugProject.blocks(before.original),b=CodeplugProject.blocks(after.original);
            boolean[][] covered=new boolean[a.length][];for(int block=0;block<a.length;block++)covered[block]=new boolean[a[block].length];
            for(CodeplugRecords.Kind kind:CodeplugRecords.Kind.values())for(int id=1;id<=CodeplugRecords.limit(kind);id++){
                int block=CodeplugRecords.block(kind,id),off=CodeplugRecords.offset(kind,id),size=CodeplugRecords.size(kind);
                Arrays.fill(covered[block],off,off+size,true);
                boolean was=CodeplugRecords.occupied(before.original,kind,id),written=CodeplugRecords.occupied(after.original,kind,id);
                int marker=(kind==CodeplugRecords.Kind.CHANNEL||kind==CodeplugRecords.Kind.ZONE)?CodeplugRecords.marker(kind,id):
                        (kind==CodeplugRecords.Kind.GROUP||kind==CodeplugRecords.Kind.SCAN)?id-1:-1;
                if(marker>=0)covered[block][marker]=true;
                boolean headerChanged=marker>=0&&(kind==CodeplugRecords.Kind.GROUP||kind==CodeplugRecords.Kind.SCAN)&&a[block][marker]!=b[block][marker];
                if(was==written&&!headerChanged&&equal(a[block],b[block],off,size))continue;
                boolean occupied=CodeplugRecords.occupied(image,kind,id);
                if(occupied==was&&equal(target[block],a[block],off,size)&&(!headerChanged||target[block][marker]==a[block][marker]))continue;
                if(occupied!=written||!equal(target[block],b[block],off,size)||headerChanged&&target[block][marker]!=b[block][marker])
                    throw new IllegalArgumentException(ProjectDiff.kindName(kind)+" #"+id+": запись изменена после резервной копии; восстановление отменено, правки сохранены");
                if(!was&&written){CodeplugRecords.delete(image,current.original,kind,id);continue;}
                System.arraycopy(a[block],off,target[block],off,size);
                if(marker>=0){if(kind==CodeplugRecords.Kind.CHANNEL||kind==CodeplugRecords.Kind.ZONE){
                    int mask=1<<((id-1)%8);target[block][marker]=(byte)((target[block][marker]&~mask)|(was?mask:0));
                }else target[block][marker]=a[block][marker];}
            }
            restoreRange(target,a,b,covered,1,0,8,"Позывной станции");restoreRange(target,a,b,covered,1,8,4,"DMR ID");restoreRange(target,a,b,covered,1,19,1,"VOX");
            restoreRange(target,a,b,covered,0,0,a[0].length,"Сведения и ограничения частот");
            restoreRange(target,a,b,covered,2,0,a[2].length,"Настройки DTMF");
            restoreRange(target,a,b,covered,7,0,12,"Загрузочный экран");
            for(int off=12;off<32;off+=2)restoreRange(target,a,b,covered,7,off,2,"Команда DMR");
            restoreRange(target,a,b,covered,7,0x28,16,"Строка загрузки 1");restoreRange(target,a,b,covered,7,0x38,16,"Строка загрузки 2");
            for(int v=0;v<2;v++)restoreRange(target,a,b,covered,7,0x78+56*v,56,"VFO");
            for(int block=0;block<a.length;block++)for(int i=0;i<a[block].length;i++)if(!covered[block][i])restoreRange(target,a,b,covered,block,i,1,"Дополнительные данные");
        });
        CodeplugIntegrity.masks(result);CodeplugIntegrity.links(result.original,result.working);return result;
    }
    private static boolean equal(byte[] a,byte[] b,int offset,int length){for(int i=offset;i<offset+length;i++)if(a[i]!=b[i])return false;return true;}
    private static void restoreRange(byte[][] target,byte[][] before,byte[][] after,boolean[][] covered,int block,int offset,int length,String label){
        Arrays.fill(covered[block],offset,offset+length,true);
        if(equal(before[block],after[block],offset,length)||equal(target[block],before[block],offset,length))return;
        if(!equal(target[block],after[block],offset,length))throw new IllegalArgumentException(label+": поле изменено после резервной копии; восстановление отменено, правки сохранены");
        System.arraycopy(before[block],offset,target[block],offset,length);
    }

}
