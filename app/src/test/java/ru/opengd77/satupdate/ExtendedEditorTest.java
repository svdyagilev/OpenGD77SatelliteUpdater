package ru.opengd77.satupdate;

import org.junit.Test;
import static org.junit.Assert.*;
import static ru.opengd77.satupdate.CodeplugEditorTest.*;
import java.io.*;
import java.util.*;

public class ExtendedEditorTest {
    static CodeplugProject extended(){
        CodeplugSnapshot s=fixture();
        Arrays.fill(s.bootAndVfos,(byte)0xa5);s.bootAndVfos[0]=0;
        CodeplugEditor.text(s.bootAndVfos,0x28,16,"Привет");CodeplugEditor.text(s.bootAndVfos,0x38,16,"UN6QCW");
        CodeplugEditor.text(s.contacts,1023*24,16,"Последний");ByteUtil.putU32be(s.contacts,1023*24+16,0x00250001);
        for(int id:new int[]{1,76}){
            s.rxGroups[id-1]=2;int off=0x80+(id-1)*80;
            CodeplugEditor.text(s.rxGroups,off,16,"Группа "+id);ByteUtil.putU16le(s.rxGroups,off+16,1);
        }
        for(int id:new int[]{1,8}){
            int o=(id-1)*64;Arrays.fill(s.aprsConfigs,o,o+64,(byte)0x5a);
            CodeplugEditor.text(s.aprsConfigs,o,8,"APRS"+id);s.aprsConfigs[o+8]=7;
            s.aprsConfigs[o+61]=(byte)0xf8;s.aprsConfigs[o+62]=0x52;s.aprsConfigs[o+63]=0x41;
            CodeplugEditor.aprs(s,id,fields("latitude","45.1234","longitude","78.5678","via1","WIDE1","via1Ssid","1","via2","WIDE2","via2Ssid","2","comment","OLD","tx","144.8"));
        }
        return new CodeplugProject(s,project().identity);
    }
    static boolean[] select(int... sections){boolean[] result=new boolean[CodeplugWritePlan.NAMES.length];for(int i:sections)result[i]=true;return result;}
    @Test public void bootChangesOnlyModeAndTextIncludingRussianYaAndEmptyLine(){
        CodeplugProject p=extended(),q=p.edit(s->CodeplugEditor.boot(s,fields("mode","1","line1","Моя рация","line2","")));
        assertEquals(1,q.model().boot.introMode);assertEquals("Моя рация",q.model().boot.line1);assertEquals("",q.model().boot.line2);
        for(int i=0;i<p.working.bootAndVfos.length;i++)if(i!=0&&(i<0x28||i>=0x48))assertEquals(p.working.bootAndVfos[i],q.working.bootAndVfos[i]);
        for(int i=0x38;i<0x48;i++)assertEquals((byte)255,q.working.bootAndVfos[i]);
        assertTrue(CodeplugProject.equal(p.working,q.undo().working));
    }
    @Test public void groupHeaderAndOneBasedReferencesAtFirstAndLastSlots(){
        for(int id:new int[]{1,76}){
            CodeplugProject p=extended(),q=p.edit(s->CodeplugEditor.rxGroup(s,id,fields("name","Новая группа","members","1024, 1")));
            int o=0x80+(id-1)*80;assertEquals(3,q.working.rxGroups[id-1]);
            assertArrayEquals(new byte[]{0,4,1,0},Arrays.copyOfRange(q.working.rxGroups,o+16,o+20));
            for(int j=o+20;j<o+80;j++)assertEquals(0,q.working.rxGroups[j]);
            for(int j=0;j<p.working.rxGroups.length;j++)if(j!=id-1&&(j<o||j>=o+80))assertEquals(p.working.rxGroups[j],q.working.rxGroups[j]);
            for(CodeplugModel.RxGroup g:q.model().rxGroups)if(g.index==id)assertEquals(Arrays.asList(1024,1),g.contactIndices);
            CodeplugProject empty=q.edit(s->CodeplugEditor.rxGroup(s,id,fields("members","")));
            assertEquals(1,empty.working.rxGroups[id-1]);assertEquals(2,empty.model().rxGroups.size());
        }
    }
    @Test public void groupRejectsMissingDuplicateAndOversizeReferencesAtomically(){
        CodeplugProject p=extended();
        StringBuilder over=new StringBuilder();for(int i=0;i<33;i++){if(i>0)over.append(',');over.append(1);}
        for(String value:new String[]{"1,1","500",over.toString()}){
            try{p.edit(s->CodeplugEditor.rxGroup(s,1,fields("name","Changed","members",value)));fail();}catch(IllegalArgumentException expected){}
            assertEquals(0,p.changedBytes());assertEquals("Группа 1",p.model().rxGroups.get(0).name);
        }
        try{p.edit(s->CodeplugEditor.rxGroup(s,2,fields("name","New")));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void aprsUsesBinaryFrequencyAndPreservesUnknownFlagsAndTail() {
        for(int id:new int[]{1,8}){
            CodeplugProject p=extended(),q=p.edit(s->CodeplugEditor.aprs(s,id,fields("tx","145.825","name","МояAPRS","ssid","15","qsy","1","baud300","1")));
            int o=(id-1)*64;
            assertEquals(14582500,ByteUtil.u32le(q.working.aprsConfigs,o+55));
            assertEquals(0xfd,q.working.aprsConfigs[o+61]&255);
            for(int pos:new int[]{29,30,59,60,62,63})assertEquals(p.working.aprsConfigs[o+pos],q.working.aprsConfigs[o+pos]);
            assertEquals("МояAPRS",q.model().aprsConfigs.get(id==1?0:1).name);
            CodeplugProject zero=q.edit(s->CodeplugEditor.aprs(s,id,fields("tx","0")));
            assertEquals(0,ByteUtil.u32le(zero.working.aprsConfigs,o+55));
        }
    }
    @Test public void coordinateWireEncodingHasSignMagnitudeAndFourDecimalDigits(){
        assertEquals((45<<15)|1234,CodeplugEditor.coordinate("45.1234",90));
        assertEquals(0x800000|(78<<15)|5678,CodeplugEditor.coordinate("-78,5678",180));
        for(int id:new int[]{1,8}){
            CodeplugProject q=extended().edit(s->CodeplugEditor.aprs(s,id,fields("latitude","-90","longitude","180","fixed","1")));
            CodeplugModel.AprsConfig a=q.model().aprsConfigs.get(id==1?0:1);
            assertEquals(-90,a.latitude,0.000001);assertEquals(180,a.longitude,0.000001);assertTrue((a.flags&2)!=0);
        }
        for(String value:new String[]{"90.0001","NaN","45.12345"})try{CodeplugEditor.coordinate(value,90);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void aprsAsciiPaddingAndValidation(){
        CodeplugProject p=extended(),q=p.edit(s->CodeplugEditor.aprs(s,1,fields("via1","wide1","via1Ssid","1","via2","","via2Ssid","0","comment","12345678901234567890123")));
        assertEquals("WIDE1",q.model().aprsConfigs.get(0).via1);
        assertEquals(0,q.working.aprsConfigs[20]);assertEquals(0,q.working.aprsConfigs[54]);
        assertEquals(23,q.model().aprsConfigs.get(0).comment.length());
        for(String[] kv:new String[][]{{"ssid","16"},{"via1","WIDE1-1"},{"comment","Привет"},{"comment","123456789012345678901234"},{"tx","144.123456"}}){
            try{p.edit(s->CodeplugEditor.aprs(s,1,fields(kv)));fail();}catch(IllegalArgumentException expected){}
        }
        try{p.edit(s->CodeplugEditor.aprs(s,2,fields("name","New")));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void noOpDoesNotNormalizeLegacyOrReservedFields(){
        CodeplugProject p=extended();assertSame(p,p.edit(s->{CodeplugEditor.boot(s,Collections.emptyMap());CodeplugEditor.rxGroup(s,1,Collections.emptyMap());CodeplugEditor.aprs(s,8,Collections.emptyMap());}));
    }
    @Test public void writeSubsetPreservesLiveVfosAndPendingAprsAndGroups()throws Exception{
        CodeplugProject p=extended().edit(s->{CodeplugEditor.boot(s,fields("line1","TEST"));CodeplugEditor.aprs(s,1,fields("ssid","9"));CodeplugEditor.rxGroup(s,76,fields("members","1024,1"));});
        CodeplugWritePlan plan=new CodeplugWritePlan(p,select(5));
        CodeplugWritePlanTest.Memory memory=new CodeplugWritePlanTest.Memory(p);
        memory.data[0x7590+16]^=1;byte[] expected=memory.data.clone();
        for(Map.Entry<Integer,Byte> e:plan.changes.entrySet())expected[e.getKey()]=e.getValue();
        plan.execute(memory,sectors->memory.backedUp=true,text->{});assertArrayEquals(expected,memory.data);
        CodeplugProject next=plan.completedProject();assertTrue(next.changedBytes()>0);
        assertArrayEquals(next.working.bootAndVfos,next.original.bootAndVfos);
        assertArrayEquals(p.original.rxGroups,next.original.rxGroups);assertArrayEquals(p.original.aprsConfigs,next.original.aprsConfigs);
        new CodeplugWritePlan(next,CodeplugWritePlan.allSections()).execute(memory,sectors->memory.backedUp=true,text->{});
    }
    @Test public void groupReferencesAreCheckedAgainstRadioBeforeWriting()throws Exception{
        CodeplugProject p=extended().edit(s->CodeplugEditor.rxGroup(s,1,fields("members","1024")));
        CodeplugWritePlanTest.Memory memory=new CodeplugWritePlanTest.Memory(p);memory.data[0xa7620]^=1;
        try{new CodeplugWritePlan(p,select(6)).execute(memory,sectors->memory.backedUp=true,text->{});fail();}catch(IOException expected){}
        assertEquals(0,memory.writes);assertFalse(memory.backedUp);
    }
    @Test public void unsupportedFieldsAndDanglingDeletionRemainBlocked(){
        for(int kind=0;kind<7;kind++){
            final int k=kind;CodeplugProject p=extended().edit(s->{
                if(k==0)s.bootAndVfos[1]^=1;if(k==1)s.bootAndVfos[0x78+0x26]^=2;
                if(k==2)s.aprsConfigs[59]^=1;if(k==3)s.aprsConfigs[61]^=0x80;
                if(k==4)s.aprsConfigs[62]^=1;if(k==5)s.rxGroups[1]=1;if(k==6){s.rxGroups[0]=0;s.channelBank0[16+43]=1;}
            });
            try{new CodeplugWritePlan(p,CodeplugWritePlan.allSections());fail();}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void extendedProjectExportRoundTripsBothImages()throws Exception{
        CodeplugProject p=extended().edit(s->{CodeplugEditor.boot(s,fields("line1","Моя рация"));CodeplugEditor.rxGroup(s,1,fields("members","1024,1"));CodeplugEditor.aprs(s,8,fields("comment","NEW"));});
        CodeplugProject loaded=CodeplugProject.read(new ByteArrayInputStream(p.encode()));
        assertTrue(CodeplugProject.equal(p.original,loaded.original));assertTrue(CodeplugProject.equal(p.working,loaded.working));
        CodeplugWritePlan plan=new CodeplugWritePlan(loaded,CodeplugWritePlan.allSections());assertTrue(plan.counts[5]>0&&plan.counts[6]>0&&plan.counts[7]>0);
    }
}
