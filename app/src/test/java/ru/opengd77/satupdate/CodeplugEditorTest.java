package ru.opengd77.satupdate;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.io.*;

public class CodeplugEditorTest {
    static CodeplugSnapshot fixture(){
        CodeplugSnapshot s=new CodeplugSnapshot(new byte[96],new byte[40],new byte[120],new byte[512],
            new byte[0x1640],new byte[2016],new byte[0x1c10],new byte[232],new byte[0xac00],
            new byte[0xc470],new byte[0x6000],new byte[0x1840],new byte[8192]);
        CodeplugEditor.text(s.generalSettings,0,8,"UN6QCW");
        ByteUtil.putU32be(s.generalSettings,8,0x04010151L);
        for(int index:new int[]{1,128,129,1024}){
            int bank=(index-1)/128,slot=(index-1)%128,base=bank==0?0:(bank-1)*0x1c10;
            byte[] b=bank==0?s.channelBank0:s.channelBanks1to7;int o=base+16+slot*56;
            b[base+slot/8]|=1<<(slot%8);
            Arrays.fill(b,o,o+56,(byte)0xa5);
            CodeplugEditor.text(b,o,16,"Канал "+index);
            ByteUtil.putU32le(b,o+16,0x14562500L);ByteUtil.putU32le(b,o+20,0x14502500L);
            b[o+24]=0;b[o+25]=8;b[o+27]=4;
            ByteUtil.putU16le(b,o+32,0x0885);ByteUtil.putU16le(b,o+34,0xffff);
            b[o+43]=0;b[o+44]=1;b[o+45]=0;ByteUtil.putU16le(b,o+46,0);
        }
        CodeplugEditor.text(s.contacts,0,16,"Контакт");ByteUtil.putU32be(s.contacts,16,0x00250000);
        s.contacts[20]=0;s.contacts[21]=(byte)0xa5;s.contacts[22]=(byte)0x5a;s.contacts[23]=(byte)0xfe;
        CodeplugEditor.text(s.dtmfContacts,0,16,"DTMF");Arrays.fill(s.dtmfContacts,16,32,(byte)0xff);s.dtmfContacts[16]=1;
        s.zones[0]=1;CodeplugEditor.text(s.zones,32,16,"Зона");ByteUtil.putU16le(s.zones,48,1);
        return s;
    }
    static CodeplugProject project(){return new CodeplugProject(fixture(),new RadioDriver.Identity("MD-9600",5,1,"RUSSIAN"));}
    static Map<String,String> fields(String... kv){Map<String,String> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put(kv[i],kv[i+1]);return m;}
    @Test public void channelFrequencyUsesBcdAndCorrectBankAtBoundaries(){
        for(int index:new int[]{1,128,129,1024}){
            CodeplugProject p=project();byte[][] before=CodeplugProject.blocks(p.working);
            CodeplugProject q=p.edit(s->CodeplugEditor.channel(s,index,fields("rx","433.512500")));
            int bank=(index-1)/128,slot=(index-1)%128,block=bank==0?6:9;
            int offset=(bank==0?0:(bank-1)*0x1c10)+16+slot*56+16;
            byte[][] after=CodeplugProject.blocks(q.working);
            for(int b=0;b<before.length;b++)for(int i=0;i<before[b].length;i++)
                if(b!=block||i<offset||i>=offset+4)assertEquals("unrelated byte "+b+":"+i,before[b][i],after[b][i]);
            assertArrayEquals(new byte[]{0x50,0x12,0x35,0x43},Arrays.copyOfRange(after[block],offset,offset+4));
            for(CodeplugModel.Channel c:q.model().channels)if(c.index==index)assertEquals(433512500,c.rxHz);
            assertEquals(0,p.changedBytes());
        }
    }
    @Test public void onlySelectedBitChangesAndOtherFieldsSurvive(){
        CodeplugProject p=project();int off=16+0x26;int old=p.working.channelBank0[off]&255;
        CodeplugProject q=p.edit(s->CodeplugEditor.channel(s,1,fields("beep","0")));
        assertEquals(old|0x40,q.working.channelBank0[off]&255);assertEquals(1,q.changedBytes());
        assertEquals(old&~0x40,(q.working.channelBank0[off]&255)&~0x40);
        assertTrue(CodeplugProject.equal(p.working,q.undo().working));
        assertTrue(CodeplugProject.equal(p.original,q.reset().working));
    }
    @Test public void generalIdAndRussianYaHaveExactWireEncoding(){
        CodeplugProject q=project().edit(s->CodeplugEditor.general(s,fields("name","Моя","id","4010151")));
        assertArrayEquals(new byte[]{(byte)0xcc,(byte)0xee,0x7f,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff},Arrays.copyOf(q.working.generalSettings,8));
        assertArrayEquals(new byte[]{0x04,0x01,0x01,0x51},Arrays.copyOfRange(q.working.generalSettings,8,12));
        assertEquals("Моя",q.model().general.radioName);assertEquals(4010151,q.model().general.dmrId);
    }
    @Test public void tonesEncodeDcsAndCtcssInTheExpectedNibbles(){
        assertEquals(0x0885,CodeplugEditor.tone("CTCSS 88,5 Гц"));
        assertEquals(0x8023,CodeplugEditor.tone("DCS 023 N"));
        assertEquals(0xc754,CodeplugEditor.tone("DCS 754 I"));
        assertEquals(0xffff,CodeplugEditor.tone("нет"));
        CodeplugProject q=project().edit(s->CodeplugEditor.channel(s,1,fields("txTone","DCS 023 I")));
        assertEquals(0xc023,ByteUtil.u16le(q.working.channelBank0,16+0x22));
    }
    @Test public void rejectedEditIsAtomicEvenAfterFirstFieldWasPatched(){
        CodeplugProject p=project();byte[] before=p.working.channelBank0.clone();
        try{p.edit(s->CodeplugEditor.channel(s,1,fields("name","Новое","rx","145.123456")));fail();}
        catch(IllegalArgumentException expected){}
        assertArrayEquals(before,p.working.channelBank0);assertEquals(0,p.changedBytes());
        try{p.edit(s->CodeplugEditor.general(s,fields("name","🙂")));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void inactiveModeFieldsAreRejectedAndModeSwitchPreservesTones(){
        CodeplugProject p=project();byte[] tones=Arrays.copyOfRange(p.working.channelBank0,48,52);
        CodeplugProject q=p.edit(s->CodeplugEditor.channel(s,1,fields("mode","1","cc","12","contact","1","group","0")));
        assertTrue(q.model().channels.get(0).digital);assertEquals(12,q.model().channels.get(0).colorCode);
        assertArrayEquals(tones,Arrays.copyOfRange(q.working.channelBank0,48,52));
        try{q.edit(s->CodeplugEditor.channel(s,1,fields("rxTone","67")));fail();}catch(IllegalArgumentException expected){}
        try{p.edit(s->CodeplugEditor.channel(s,1,fields("mode","1","contact","900")));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void contactsAndZonesRoundTripAndReservedContactBitsSurvive(){
        CodeplugProject p=project();
        CodeplugProject q=p.edit(s->CodeplugEditor.contact(s,1,false,fields("name","Моя группа","number","25099","ts","0")))
            .edit(s->CodeplugEditor.contact(s,1,true,fields("name","Вызов","code","12AB*#")))
            .edit(s->CodeplugEditor.zone(s,1,fields("name","Моя зона","members","1024, 129, 1")));
        assertEquals(25099,q.model().contacts.get(0).number);
        assertEquals((byte)0xfc,q.working.contacts[23]);assertEquals((byte)0xa5,q.working.contacts[21]);assertEquals((byte)0x5a,q.working.contacts[22]);
        assertEquals("12AB*#",q.model().dtmfContacts.get(0).code);
        assertEquals(Arrays.asList(1024,129,1),q.model().zones.get(0).channelIndices);
        try{q.edit(s->CodeplugEditor.zone(s,1,fields("members","1,1")));fail();}catch(IllegalArgumentException expected){}
        try{q.edit(s->CodeplugEditor.zone(s,1,fields("members","100")));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void saveReopenPreservesBothImagesAndDetectsCorruption()throws Exception{
        CodeplugProject p=project().edit(s->CodeplugEditor.channel(s,1,fields("name","Моя станция")));
        byte[] bytes=p.encode();CodeplugProject read=CodeplugProject.read(new ByteArrayInputStream(bytes));
        assertTrue(CodeplugProject.equal(p.original,read.original));assertTrue(CodeplugProject.equal(p.working,read.working));
        assertEquals("RUSSIAN",read.identity.firmware);assertEquals(p.changedBytes(),read.changedBytes());
        assertTrue(CodeplugProject.equal(read.original,read.reset().working));
        bytes[100]^=1;try{CodeplugProject.read(new ByteArrayInputStream(bytes));fail();}catch(IOException expected){}
    }
    @Test public void noOpEditDoesNotNormalizeUnknownBytes(){
        CodeplugProject p=project();assertSame(p,p.edit(s->CodeplugEditor.channel(s,1,Collections.emptyMap())));
    }
}
