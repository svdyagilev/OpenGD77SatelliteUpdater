package ru.opengd77.satupdate;

import java.io.IOException;
import java.util.Arrays;

/** Read-only snapshot reader for the two documented MD-9600 callsign regions. */
final class CallsignRadioReader {
    interface Memory {
        byte[] read(int address,int length,OpenGd77Protocol.ReadProgress progress)throws IOException;
    }
    interface Progress { void show(String message); }

    static CallsignDatabase read(Memory memory,Progress progress)throws IOException {
        progress.show("Чтение заголовка базы позывных...");
        byte[] header=memory.read(CallsignDatabase.BASE,CallsignDatabase.HEADER,(done,total)->{});
        if(header.length!=CallsignDatabase.HEADER)throw new IOException("Неполное чтение заголовка базы");
        if(header[0]!='I'||header[1]!='d'||header[2]!='N'||header[4]!='0'||header[5]!='0'||header[6]!='1'||header[7]!=0)
            throw new IOException("В рации не найдена база позывных IdN001");
        int recordSize=(header[3]&255)-0x4a;
        int chars=CallsignDatabase.charsForRecordSize(recordSize);
        long count=ByteUtil.u32le(header,8);
        if(count>CallsignDatabase.capacity(chars))throw new IOException("Повреждён счётчик записей базы: "+count);
        int n0=Math.min((int)count,(CallsignDatabase.SIZE0-CallsignDatabase.HEADER)/recordSize);
        int firstLength=CallsignDatabase.HEADER+n0*recordSize;
        int secondLength=((int)count-n0)*recordSize;
        progress.show("Чтение части 1 из 2: "+n0+" записей, длина "+chars+" символов...");
        byte[] first=memory.read(CallsignDatabase.BASE,firstLength,(done,total)->{
            if(done==total||done%64==0)progress.show("Чтение FLASH: "+done+" / "+total+" блоков");
        });
        if(first.length!=firstLength||!Arrays.equals(header,Arrays.copyOf(first,CallsignDatabase.HEADER)))
            throw new IOException("Заголовок базы изменился во время чтения; повторите операцию");
        byte[] second=new byte[0];
        if(secondLength>0) {
            progress.show("Чтение части 2 из 2: "+(count-n0)+" записей...");
            second=memory.read(CallsignDatabase.BASE1,secondLength,(done,total)->{
                if(done==total||done%64==0)progress.show("Чтение FLASH: "+done+" / "+total+" блоков");
            });
            if(second.length!=secondLength)throw new IOException("Неполное чтение второй части базы");
        }
        CallsignDatabase db=CallsignDatabase.fromRadio(first,second);
        progress.show("Чтение завершено: "+db.entries.size()+" записей.");
        return db;
    }
}
