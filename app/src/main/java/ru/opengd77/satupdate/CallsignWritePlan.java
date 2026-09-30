package ru.opengd77.satupdate;

import java.io.*;
import java.util.*;

/** Only the two documented MD-9600 callsign regions can be written. */
final class CallsignWritePlan {
    interface Memory { byte[] read(int address,int length)throws IOException;void write(int address,byte[] bytes)throws IOException; }
    interface Backup { void save(List<Sector> sectors)throws IOException; }
    interface Progress { void show(String text); }
    static final class Sector {final int address;final byte[] before,after;Sector(int a,byte[] b,byte[] n){address=a;before=b;after=n;}}
    static void checkRadio(long type,long version,long flashId)throws IOException {
        if(type!=5)throw new IOException("Запись базы поддерживается только для MD-9600");
        if(version!=3&&version!=4)throw new IOException("Неподдерживаемая версия Radio Info: "+version);
        // RUS firmware v4 reports DEFECA7E as a sentinel where flashId is unavailable.
        // Reject a real, readable ID for a different capacity; accept that explicit sentinel.
        if(flashId!=0xdefeca7eL && flashId!=0 && (flashId&0xffff)!=0x4018)
            throw new IOException("Идентификатор FLASH 0x"+Long.toHexString(flashId)+" не соответствует проверенной памяти MD-9600. Запись отменена.");
    }
    private static boolean allowed(int a){return a%4096==0&&((a>=CallsignDatabase.BASE&&a+4096<=CallsignDatabase.BASE+CallsignDatabase.SIZE0)||(a>=CallsignDatabase.BASE1&&a+4096<=CallsignDatabase.BASE1+CallsignDatabase.SIZE1));}
    static List<Sector> prepare(CallsignDatabase db,Memory memory,Progress log)throws IOException {
        List<Sector> sectors=new ArrayList<>();
        byte[][] data={db.first,db.second};int[] bases={CallsignDatabase.BASE,CallsignDatabase.BASE1};
        int total=(db.first.length+4095)/4096+(db.second.length+4095)/4096,done=0;
        for(int part=0;part<2;part++)for(int off=0;off<data[part].length;off+=4096){
            int address=bases[part]+off;if(!allowed(address))throw new IOException("База вышла за допустимую область памяти");
            byte[] old=memory.read(address,4096);if(old.length!=4096)throw new IOException("Неполное чтение сектора");
            byte[] next=old.clone();System.arraycopy(data[part],off,next,0,Math.min(4096,data[part].length-off));
            sectors.add(new Sector(address,old.clone(),next));log.show("Чтение базы: "+(++done)+" / "+total+" секторов");
        }
        return sectors;
    }
    static boolean execute(List<Sector> sectors,Memory memory,Backup backup,Progress log)throws IOException {
        if(sectors.isEmpty()||sectors.get(0).address!=CallsignDatabase.BASE)throw new IOException("Нет заголовка базы");
        Set<Integer> seen=new HashSet<>();boolean changed=false;
        for(Sector s:sectors){if(!allowed(s.address)||!seen.add(s.address)||s.before.length!=4096||s.after.length!=4096)throw new IOException("Недопустимый план записи");changed|=!Arrays.equals(s.before,s.after);}
        if(!changed){log.show("База уже совпадает: запись не требуется.");return false;}
        backup.save(sectors);log.show("Резервная копия сохранена. Контрольное чтение...");
        int done=0;
        for(Sector s:sectors){if(!Arrays.equals(s.before,memory.read(s.address,4096)))throw new IOException("Память изменилась после подготовки. Запись отменена.");log.show("Контроль перед записью: "+(++done)+" / "+sectors.size());}
        Sector header=sectors.get(0);
        // While replacing multiple sectors, hide the database from the firmware. Publish header last.
        if(sectors.size()>1){byte[] invalid=header.before.clone();Arrays.fill(invalid,0,12,(byte)0);verified(memory,header.address,invalid);}
        done=0;
        for(int i=1;i<sectors.size();i++){Sector s=sectors.get(i);if(!Arrays.equals(s.before,s.after))verified(memory,s.address,s.after);log.show("Запись и проверка: "+(++done)+" / "+sectors.size());}
        verified(memory,header.address,header.after);log.show("Запись и проверка: "+sectors.size()+" / "+sectors.size());
        return true;
    }
    private static void verified(Memory m,int address,byte[] data)throws IOException {
        m.write(address,data);
        if(!Arrays.equals(data,m.read(address,4096)))throw new IOException("Ошибка read-back FLASH 0x"+Integer.toHexString(address)+". Запись остановлена.");
    }
}
