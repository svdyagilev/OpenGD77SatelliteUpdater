package ru.opengd77.satupdate;

import org.junit.Test;
import static org.junit.Assert.*;
import static ru.opengd77.satupdate.CodeplugEditorTest.*;
import static ru.opengd77.satupdate.CodeplugRecords.Kind.*;
import java.util.*;
import java.io.*;

public class FullEditorTest {
    private static boolean[] selected(int... sections){boolean[] out=new boolean[CodeplugWritePlan.NAMES.length];for(int i:sections)out[i]=true;return out;}
    private CodeplugProject baseline(){
        CodeplugSnapshot s=fixture();
        CodeplugRecords.create(s,GROUP,1,fields("name","Группа","members","1"));
        CodeplugRecords.create(s,SCAN,1,fields("name","Скан","members","1,129,-1","primary","1","secondary","129","revert","-1"));
        CodeplugRecords.create(s,APRS,1,fields("name","APRS","via1","WIDE1","via1Ssid","1"));
        CodeplugEditor.channel(s,1,fields("mode","1","contact","1","group","1"));
        CodeplugEditor.channel(s,129,fields("aprs","1"));
        System.arraycopy(s.channelBank0,16,s.bootAndVfos,0x78,56);
        System.arraycopy(s.channelBank0,16,s.bootAndVfos,0xb0,56);
        ByteUtil.putU16le(s.bootAndVfos,12,1);
        return new CodeplugProject(s,project().identity);
    }
    private CodeplugProject remove(CodeplugProject p,CodeplugRecords.Kind k,int id){return p.edit(s->CodeplugRecords.delete(s,p.original,k,id));}
    private void execute(CodeplugWritePlan plan,CodeplugWritePlanTest.Memory m)throws Exception{
        plan.execute(m,sectors->{assertEquals(0,m.writes);m.backedUp=true;},message->{});
    }
    @Test public void newGroupScanAndAprsRoundTripIncludingLastSlots()throws Exception{
        CodeplugProject p=project().edit(s->{
            CodeplugRecords.create(s,GROUP,76,fields("name","Последняя","members","1"));
            CodeplugRecords.create(s,SCAN,64,fields("name","Последний","members","-1,1,1024","primary","-1","secondary","1024","revert","1","hold","750","sample","1500"));
            CodeplugRecords.create(s,APRS,8,fields("name","Моя APRS","ssid","7","tx","144.8","latitude","-43.1234","longitude","76.5432","fixed","1","iconTable","/","icon",">"));
        });
        assertEquals(2,p.working.rxGroups[75]);assertEquals(1,p.working.scanLists[63]);
        int off=CodeplugRecords.offset(SCAN,64);assertEquals(1,ByteUtil.u16le(p.working.scanLists,off+16));assertEquals(2,ByteUtil.u16le(p.working.scanLists,off+18));assertEquals(1025,ByteUtil.u16le(p.working.scanLists,off+20));
        assertEquals(Arrays.asList(-1,1,1024),p.model().scanLists.get(0).channelIndices);assertEquals(-1,p.model().scanLists.get(0).primary);
        assertEquals(0x4152,ByteUtil.u16le(p.working.aprsConfigs,7*64+62));
        CodeplugProject read=CodeplugProject.read(new ByteArrayInputStream(p.encode()));assertTrue(CodeplugProject.equal(p.working,read.working));
        CodeplugWritePlan plan=new CodeplugWritePlan(read,CodeplugWritePlan.allSections());CodeplugWritePlanTest.Memory m=new CodeplugWritePlanTest.Memory(read);execute(plan,m);
        assertEquals(0,plan.completedProject().changedBytes());
    }
    @Test public void contactDeleteClearsChannelsGroupsVfosAndQuickKeysAndIsUndoable()throws Exception{
        CodeplugProject p=baseline(),q=remove(p,DMR,1);
        assertEquals(0,q.model().contacts.size());assertEquals(0,q.model().channels.get(0).contactIndex);
        assertTrue(q.model().rxGroups.get(0).contactIndices.isEmpty());assertEquals(1,q.working.rxGroups[0]);
        assertEquals(0,q.model().vfos.get(0).contactIndex);assertEquals(0,q.model().vfos.get(1).contactIndex);
        assertEquals(0x8000,ByteUtil.u16le(q.working.bootAndVfos,12));
        assertTrue(CodeplugProject.equal(p.working,q.undo().working));
        CodeplugWritePlan plan=new CodeplugWritePlan(q,CodeplugWritePlan.allSections());CodeplugWritePlanTest.Memory m=new CodeplugWritePlanTest.Memory(q);
        byte[] expected=m.data.clone();for(Map.Entry<Integer,Byte> e:plan.changes.entrySet())expected[e.getKey()]=e.getValue();execute(plan,m);assertArrayEquals(expected,m.data);
    }
    @Test public void channelDeleteCompactsZonesAndScanAndClearsPriorities(){
        CodeplugProject p=baseline(),q=remove(p,CHANNEL,1);
        assertFalse(CodeplugRecords.occupied(q.working,CHANNEL,1));assertTrue(q.model().zones.get(0).channelIndices.isEmpty());
        assertEquals(Arrays.asList(129,-1),q.model().scanLists.get(0).channelIndices);
        assertEquals(0,q.model().scanLists.get(0).primary);assertEquals(129,q.model().scanLists.get(0).secondary);assertEquals(-1,q.model().scanLists.get(0).revert);
        new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
    }
    @Test public void groupAndAprsDeleteClearReferencesIncludingHiddenModes(){
        CodeplugProject p=baseline(),q=remove(remove(p,GROUP,1),APRS,1);
        assertTrue(q.model().rxGroups.isEmpty());assertTrue(q.model().aprsConfigs.isEmpty());
        assertEquals(0,q.model().channels.get(0).rxGroupIndex);assertEquals(0,q.model().vfos.get(1).rxGroupIndex);
        for(CodeplugModel.Channel c:q.model().channels)if(c.index==129)assertEquals(0,c.aprsConfigIndex);
        new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
    }
    @Test public void everyKindCanBeCreatedThenDeletedWithoutLeavingGarbage(){
        for(CodeplugRecords.Kind kind:CodeplugRecords.Kind.values()){
            CodeplugProject p=project();int id=CodeplugRecords.next(p.working,kind);
            Map<String,String> f=kind==CHANNEL?fields("name","Новый","rx","145.5","tx","145.5"):kind==DMR?fields("name","Новый","number","25099"):kind==DTMF?fields("name","Новый","code","123"):fields("name",kind==APRS?"APRS":"Новый");
            CodeplugProject q=p.edit(s->CodeplugRecords.create(s,kind,id,f));q=remove(q,kind,id);
            assertTrue(kind.toString(),CodeplugProject.equal(p.working,q.working));new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
        }
    }
    @Test public void deletedExistingSlotCanBeReusedBeforeWriting(){
        CodeplugProject p=baseline(),q=remove(p,CHANNEL,1);
        q=q.edit(s->CodeplugRecords.create(s,CHANNEL,1,fields("name","Замена","rx","433.5","tx","433.5")));
        assertEquals("Замена",q.model().channels.get(0).name);assertTrue(q.model().zones.get(0).channelIndices.isEmpty());
        new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
    }
    @Test public void deletionSubsetMustIncludeDependentCleanup(){
        CodeplugProject p=remove(baseline(),DMR,1);
        for(int[] sections:new int[][]{{2},{1,2,6},{2,6,11}}){
            try{new CodeplugWritePlan(p,selected(sections));fail();}catch(IllegalArgumentException expected){}
        }
        new CodeplugWritePlan(p,selected(1,2,6,11));
        CodeplugProject q=remove(baseline(),CHANNEL,1);
        try{new CodeplugWritePlan(q,selected(1));fail();}catch(IllegalArgumentException expected){}
        new CodeplugWritePlan(q,selected(1,4,8));
    }
    @Test public void staleVfoDependencyBlocksContactDeletionBeforeBackup()throws Exception{
        CodeplugProject p=remove(baseline(),DMR,1);CodeplugWritePlan plan=new CodeplugWritePlan(p,CodeplugWritePlan.allSections());
        CodeplugWritePlanTest.Memory m=new CodeplugWritePlanTest.Memory(p);m.data[0x7518+0x78+16]^=1;
        try{execute(plan,m);fail();}catch(IOException expected){}
        assertEquals(0,m.writes);assertFalse(m.backedUp);
    }
    @Test public void sharedBlocksCountAndRebaseOnlySelectedFields()throws Exception{
        CodeplugProject p=baseline().edit(s->{CodeplugEditor.general(s,fields("name","TEST"));CodeplugLists.radio(s,fields("vox","7"));CodeplugEditor.boot(s,fields("line1","Привет"));CodeplugLists.vfo(s,0,fields("rx","433.5"));});
        CodeplugWritePlan first=new CodeplugWritePlan(p,selected(0,5));CodeplugWritePlanTest.Memory m=new CodeplugWritePlanTest.Memory(p);execute(first,m);
        CodeplugProject q=first.completedProject();assertEquals(p.original.generalSettings[19],q.original.generalSettings[19]);
        assertArrayEquals(Arrays.copyOfRange(p.original.bootAndVfos,0x78,0xe8),Arrays.copyOfRange(q.original.bootAndVfos,0x78,0xe8));
        assertEquals(0,new CodeplugWritePlan(q,selected(0,5)).changes.size());
        CodeplugWritePlan second=new CodeplugWritePlan(q,selected(10,11));execute(second,new CodeplugWritePlanTest.Memory(q));assertEquals(0,second.completedProject().changedBytes());
    }
    @Test public void dtmfAndBandsAndCoordinatesPreserveReservedFields(){
        CodeplugProject p=baseline();
        CodeplugProject q=p.edit(s->{CodeplugLists.dtmf(s,fields("self","12A#","up","123←","rate","8","tail","300","responseHold","1.5"));CodeplugLists.bands(s,fields("uhfMin","400","uhfMax","470","vhfMin","136","vhfMax","174"));CodeplugEditor.channel(s,1,fields("latitude","-43.1234","longitude","76.5432","location","1"));});
        assertEquals("12A#",q.model().dtmfSettings.selfId);assertEquals("123←",q.model().dtmfSettings.pttUp);assertEquals(300,q.model().dtmfSettings.tailMs);
        assertEquals(0xff,q.working.dtmfSettings[4]&255);assertEquals(p.working.dtmfSettings[45],q.working.dtmfSettings[45]);
        assertEquals(400,q.model().deviceInfo.minUhf);assertEquals(-43.1234,q.model().channels.get(0).latitude,0.00001);
        assertEquals(76.5432,q.model().channels.get(0).longitude,0.00001);assertTrue(q.model().channels.get(0).useLocation);
        new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
    }
    @Test public void malformedListAndReservedSettingsAreRejectedAtomically(){
        CodeplugProject p=baseline();
        try{p.edit(s->CodeplugLists.scan(s,1,fields("name","Test","members","1,1")));fail();}catch(IllegalArgumentException expected){}
        try{p.edit(s->CodeplugLists.dtmf(s,fields("rate","11")));fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,p.changedBytes());
        CodeplugProject bad=p.edit(s->s.dtmfSettings[45]=1);try{new CodeplugWritePlan(bad,CodeplugWritePlan.allSections());fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void rawDeletionCannotBypassReferenceChecks(){
        CodeplugProject p=baseline().edit(s->s.contacts[0]=(byte)255);
        try{new CodeplugWritePlan(p,CodeplugWritePlan.allSections());fail();}catch(IllegalArgumentException expected){}
    }
}
