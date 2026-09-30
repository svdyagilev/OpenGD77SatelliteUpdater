package ru.opengd77.satupdate;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class CallsignDatabaseTest {
    private CallsignDatabase.Options options(String region,int chars){return new CallsignDatabase.Options(region,new boolean[]{true,true,true,true,true}," ",chars);}
    private CallsignDatabase read(String csv,String region,int chars)throws IOException{return CallsignDatabase.read(new StringReader(csv),options(region,chars));}
    @Test public void regionColumnsSortingDedupAndExactPreview()throws Exception {
        String csv="\ufeffRADIO_ID,CALLSIGN,FIRST_NAME,LAST_NAME,CITY,STATE,COUNTRY\r\n4010159,UN4GVI,Vyacheslav,,Almaty,None,Kazakhstan\r\n2500001,R1A,Other,,,,\r\n4010154,UN7PPV,Sergey,,Karaganda,Karaganda,Kazakhstan\r\n4010154,UN7PPV,Duplicate,,,,\r\nwrong,BAD,,,,,\r\n";
        CallsignDatabase d=read(csv,"401",16);assertEquals(2,d.entries.size());assertEquals(1,d.duplicates);assertEquals(1,d.skipped);assertEquals(5,d.sourceRows);
        assertEquals(4010154,d.entries.get(0).id);assertEquals("UN7PPV Sergey Ka",d.entries.get(0).text);
        assertEquals(937163,CallsignDatabase.capacity(16));assertEquals(15,d.recordSize);
        assertArrayEquals(new byte[]{'I','d','N',0x59,'0','0','1',0,2,0,0,0},Arrays.copyOf(d.first,12));
        for(int i=0;i<d.entries.size();i++){int off=12+i*d.recordSize;int id=(d.first[off]&255)|((d.first[off+1]&255)<<8)|((d.first[off+2]&255)<<16);assertEquals(d.entries.get(i).id,id);assertEquals(d.entries.get(i).text,CallsignDatabase.unpack(d.first,off+3,16));}
    }
    @Test public void csvQuotedCommaEscapesAndMultiline()throws Exception {
        CallsignDatabase d=read("ID,Callsign,First Name,City\n4010001,UN1A,\"Sergey \"\"S\"\"\",\"Almaty,\nCity\"\n","",48);
        assertEquals("UN1A Sergey .S. Almaty. City",d.entries.get(0).text);
        d=read("DMRID;CALL;NAME;COUNTRY\r4010002;UN2A;Андрей;Казахстан\r","401,250",48);
        assertEquals("UN2A Andrey Kazakhstan",d.entries.get(0).text);
    }
    @Test public void disabledFieldsAndNormalizationAreVisible()throws Exception {
        CallsignDatabase.Options o=new CallsignDatabase.Options("401",new boolean[]{true,false,false,false,false},".",32);
        CallsignDatabase d=CallsignDatabase.read(new StringReader("ID,CALLSIGN,FIRST_NAME,LAST_NAME,CITY\n4010001,UN1A,José,Hidden,Hidden\n"),o);
        assertEquals("UN1A.Jose",d.entries.get(0).text);
        assertEquals("Yozh Shchuka",CallsignDatabase.normalize("Ёж Щука"));
        assertArrayEquals(new byte[]{(byte)0x2c,(byte)0xc3,(byte)0x4e},CallsignDatabase.pack("ABCD",4));
    }
    @Test public void invalidCsvAndOptionsDoNotProduceWritableEmptyDatabase()throws Exception {
        for(String csv:new String[]{"<html>Error</html>","ID,CALLSIGN\n4010001,\"UNCLOSED", "ID,CALLSIGN\n0,A\n16777215,B\n", "ID,CALLSIGN\n2500001,R1A\n"}){
            try{read(csv,"401",16);fail(csv);}catch(IOException expected){}
        }
        try{options("401-250",16);fail();}catch(IllegalArgumentException expected){}
        try{options("401",17);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void splitAtWholeRecordsAndAllLengthsRoundTrip()throws Exception {
        for(int chars:CallsignDatabase.LENGTHS){StringBuilder csv=new StringBuilder("ID,CALLSIGN,NAME\n");int size=3+chars*3/4,n0=(CallsignDatabase.SIZE0-12)/size;
            for(int i=0;i<n0+1;i++)csv.append(1000000+i).append(",A1A,Name\n");
            CallsignDatabase d=read(csv.toString(),"",chars);assertEquals(12+n0*size,d.first.length);assertEquals(size,d.second.length);
            int id=(d.second[0]&255)|((d.second[1]&255)<<8)|((d.second[2]&255)<<16);assertEquals(1000000+n0,id);
            assertEquals("A1A Name",CallsignDatabase.unpack(d.second,3,chars));
        }
    }
    @Test public void explicitClearHasValidZeroCountHeader(){CallsignDatabase d=CallsignDatabase.empty(16);assertEquals(12,d.first.length);assertEquals(0,ByteUtil.u32le(d.first,8));assertEquals(0,d.second.length);}
}
