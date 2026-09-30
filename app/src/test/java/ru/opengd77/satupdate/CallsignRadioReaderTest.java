package ru.opengd77.satupdate;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class CallsignRadioReaderTest {
    private static CallsignDatabase database(int chars,int count)throws Exception {
        StringBuilder csv=new StringBuilder("ID,CALLSIGN\n");
        for(int i=0;i<count;i++)csv.append(4010000+i).append(",UN1A\n");
        return CallsignDatabase.read(new StringReader(csv.toString()),new CallsignDatabase.Options("",new boolean[5]," ",chars));
    }
    private static class Memory implements CallsignRadioReader.Memory {
        final CallsignDatabase source;final List<Integer> addresses=new ArrayList<>();
        Memory(CallsignDatabase source){this.source=source;}
        public byte[] read(int address,int length,OpenGd77Protocol.ReadProgress progress)throws IOException {
            addresses.add(address);byte[] src;
            int offset;
            if(address>=CallsignDatabase.BASE&&address<CallsignDatabase.BASE+CallsignDatabase.SIZE0){src=source.first;offset=address-CallsignDatabase.BASE;}
            else if(address>=CallsignDatabase.BASE1&&address<CallsignDatabase.BASE1+CallsignDatabase.SIZE1){src=source.second;offset=address-CallsignDatabase.BASE1;}
            else throw new IOException("unexpected address "+address);
            if(offset<0||offset+length>src.length)throw new IOException("unexpected read length");
            progress.onProgress(1,1);return Arrays.copyOfRange(src,offset,offset+length);
        }
    }
    @Test public void readsAndDecodesSplitDatabaseWithoutWriting()throws Exception {
        int record=3+48*3/4;int firstCapacity=(CallsignDatabase.SIZE0-CallsignDatabase.HEADER)/record;
        CallsignDatabase source=database(48,firstCapacity+2);Memory memory=new Memory(source);
        CallsignDatabase loaded=CallsignRadioReader.read(memory,text->{});
        assertEquals(source.entries.size(),loaded.entries.size());assertEquals(48,loaded.chars);
        assertArrayEquals(source.first,loaded.first);assertArrayEquals(source.second,loaded.second);
        assertEquals(Arrays.asList(CallsignDatabase.BASE,CallsignDatabase.BASE,CallsignDatabase.BASE1),memory.addresses);
    }
    @Test public void refusesUnknownOrEmptyMemoryBeforeAnyLargeRead()throws Exception {
        CallsignRadioReader.Memory blank=(address,length,progress)->new byte[length];
        try{CallsignRadioReader.read(blank,text->{});fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("IdN001"));}
    }
}
