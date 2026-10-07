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
        assertEquals(4010154,d.entries.get(0).id);assertEquals("UN7PPV Sergey Karaganda Karaganda Kazakhstan",d.entries.get(0).text);assertEquals("UN7PPV Sergey Ka",d.entries.get(0).encoded);
        assertEquals(937163,CallsignDatabase.capacity(16));assertEquals(15,d.recordSize);
        assertArrayEquals(new byte[]{'I','d','N',0x59,'0','0','1',0,2,0,0,0},Arrays.copyOf(d.first,12));
        for(int i=0;i<d.entries.size();i++){int off=12+i*d.recordSize;int id=(d.first[off]&255)|((d.first[off+1]&255)<<8)|((d.first[off+2]&255)<<16);assertEquals(d.entries.get(i).id,id);assertEquals(d.entries.get(i).encoded,CallsignDatabase.unpack(d.first,off+3,16));}
    }
    @Test public void csvQuotedCommaEscapesAndMultiline()throws Exception {
        CallsignDatabase d=read("ID,Callsign,First Name,City\n4010001,UN1A,\"Sergey \"\"S\"\"\",\"Almaty,\nCity\"\n","",48);
        assertEquals("UN1A Sergey .S. Almaty. City",d.entries.get(0).text);assertEquals("UN1A Sergey .S. Almaty. City",d.entries.get(0).encoded);
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
            assertEquals(d.entries.get(n0).encoded,CallsignDatabase.unpack(d.second,3,chars));
        }
    }
    @Test public void explicitClearHasValidZeroCountHeader(){CallsignDatabase d=CallsignDatabase.empty(16);assertEquals(12,d.first.length);assertEquals(0,ByteUtil.u32le(d.first,8));assertEquals(0,d.second.length);}
    @Test public void radioImageRoundTripsForEverySupportedLength()throws Exception {
        for(int chars:CallsignDatabase.LENGTHS){
            CallsignDatabase source=read("ID,CALLSIGN,NAME\n4010001,UN1A,Almaty\n4010002,UN2B,Karaganda\n","",chars);
            CallsignDatabase decoded=CallsignDatabase.fromRadio(source.first,source.second);
            assertEquals(chars,decoded.chars);assertEquals(source.entries.size(),decoded.entries.size());
            assertEquals(source.entries.get(0).id,decoded.entries.get(0).id);
            assertEquals(source.entries.get(0).encoded,decoded.entries.get(0).encoded);
            assertArrayEquals(source.first,decoded.first);assertArrayEquals(source.second,decoded.second);
        }
    }
    @Test public void editorAddsEditsAndDeletesSortedEntries()throws Exception {
        CallsignDatabase d=read("ID,CALLSIGN\n4010002,UN2B\n","",16);
        d=d.withEntry(null,CallsignDatabase.manualEntry("4010001","UN1A Almaty",16));
        assertEquals(4010001,d.entries.get(0).id);assertEquals(2,d.entries.size());
        d=d.withEntry(4010001,CallsignDatabase.manualEntry("4010003","UN3C Karaganda",16));
        assertEquals(4010002,d.entries.get(0).id);assertEquals(4010003,d.entries.get(1).id);
        try{d.withEntry(null,CallsignDatabase.manualEntry("4010002","Duplicate",16));fail();}catch(IOException expected){}
        d=d.withoutEntry(4010002);assertEquals(1,d.entries.size());assertEquals(4010003,d.entries.get(0).id);
        try{CallsignDatabase.manualEntry("16777215","bad",16);fail();}catch(IOException expected){}
    }
    @Test public void radioImageRejectsInvalidHeaderAndCount()throws Exception {
        CallsignDatabase d=read("ID,CALLSIGN\n4010001,UN1A\n","",16);
        byte[] damaged=d.first.clone();damaged[0]='X';
        try{CallsignDatabase.fromRadio(damaged,d.second);fail();}catch(IOException expected){}
        damaged=d.first.clone();ByteUtil.putU32le(damaged,8,CallsignDatabase.capacity(16)+1L);
        try{CallsignDatabase.fromRadio(damaged,d.second);fail();}catch(IOException expected){}
    }

    @Test public void structuredFieldsKeepMultiwordBoundariesAndUseRadioEncoding()throws Exception {
        String[] fields={"UN6QCW","Сергей","Талдыкорган","Жетысу","Republic of Kazakhstan"};
        CallsignDatabase.Entry entry=CallsignDatabase.structuredEntry("4010151",fields,48," ");
        assertArrayEquals(fields,entry.details);assertEquals("UN6QCW Sergey Taldykorgan Zhetysu Republic of Kazakhstan",entry.text);
        CallsignDatabase d=CallsignDatabase.empty(48).withEntry(null,entry);
        assertEquals(entry.encoded,CallsignDatabase.fromRadio(d.first,d.second).entries.get(0).text);
        assertArrayEquals(fields,d.entries.get(0).details);fields[0]="Changed";assertEquals("UN6QCW",entry.details[0]);
        try{CallsignDatabase.structuredEntry("4010151",new String[]{"","Name","City","State","Country"},48," ");fail();}catch(IOException expected){}
    }
    @Test public void csvKeepsSelectedDetailsAndDoesNotInventRadioBoundaries()throws Exception {
        CallsignDatabase d=read("ID,CALLSIGN,FIRST_NAME,LAST_NAME,CITY,STATE,COUNTRY\n4010001,UN1A,John,Doe,New York,New York,United States\n","",48);
        assertArrayEquals(new String[]{"UN1A","John Doe","New York","New York","United States"},d.entries.get(0).details);
        assertNull(CallsignDatabase.fromRadio(d.first,d.second).entries.get(0).details);
        CallsignDatabase.Options o=new CallsignDatabase.Options("",new boolean[]{true,false,false,false,true}," ",48);
        CallsignDatabase filtered=CallsignDatabase.read(new StringReader("ID,CALLSIGN,NAME,LAST_NAME,CITY,COUNTRY\n4010001,UN1A,John,Hidden,Hidden,United States\n"),o);
        assertArrayEquals(new String[]{"UN1A","John","","","United States"},filtered.entries.get(0).details);
    }

    @Test public void unchangedCsvFieldsKeepOriginalSeparatorsWhenOnlyIdChanges()throws Exception {
        CallsignDatabase.Options o=new CallsignDatabase.Options("",new boolean[]{true,true,true,true,true},".",48);
        CallsignDatabase d=CallsignDatabase.read(new StringReader("ID,CALLSIGN,NAME,LAST_NAME,CITY\n4010001,UN1A,John,Doe,New York\n"),o);
        CallsignDatabase.Entry old=d.entries.get(0),edited=CallsignDatabase.structuredReplacement("4010002",old.details,48,".",old);
        assertEquals("UN1A.John.Doe.New York",edited.text);assertEquals(4010002,edited.id);
    }
}
