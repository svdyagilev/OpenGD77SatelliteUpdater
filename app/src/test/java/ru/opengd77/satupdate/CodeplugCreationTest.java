package ru.opengd77.satupdate;

import org.junit.Test;
import static org.junit.Assert.*;
import static ru.opengd77.satupdate.CodeplugEditorTest.*;
import static ru.opengd77.satupdate.CodeplugRecords.Kind.*;
import java.util.*;
import java.io.*;

public class CodeplugCreationTest {
    private CodeplugProject add(CodeplugProject p,CodeplugRecords.Kind kind,Map<String,String> f){
        int index=CodeplugRecords.next(p.working,kind);
        return p.edit(s->CodeplugRecords.create(s,kind,index,f));
    }
    private CodeplugProject linked(){
        CodeplugProject p=add(project(),DMR,fields("name","Моя группа","number","25099","type","0","ts","3"));
        p=add(p,DTMF,fields("name","Вызов","code","0123456789ABCD*#"));
        p=add(p,CHANNEL,fields("name","Цифровой","rx","433.5","tx","433.5","mode","1","contact","2","cc","1","ts","2"));
        return add(p,ZONE,fields("name","Новая зона","members","2,1"));
    }
    @Test public void createsAllFourTypesWithoutChangingExistingRecordsAndRoundTrips()throws Exception{
        CodeplugProject p=project(),q=linked();
        assertEquals(2,q.model().contacts.size());assertEquals(2,q.model().dtmfContacts.size());
        assertEquals(5,q.model().channels.size());assertEquals(2,q.model().zones.size());
        assertEquals("Моя группа",q.model().contacts.get(1).name);
        assertEquals("0123456789ABCD*#",q.model().dtmfContacts.get(1).code);
        assertEquals(Arrays.asList(2,1),q.model().zones.get(1).channelIndices);
        assertEquals(2,q.model().channels.get(1).contactIndex);assertEquals(2,q.model().channels.get(1).timeSlot);
        assertArrayEquals(Arrays.copyOf(p.working.contacts,24),Arrays.copyOf(q.working.contacts,24));
        assertArrayEquals(Arrays.copyOfRange(p.working.channelBank0,16,72),Arrays.copyOfRange(q.working.channelBank0,16,72));
        CodeplugProject read=CodeplugProject.read(new ByteArrayInputStream(q.encode()));
        assertTrue(CodeplugProject.equal(q.working,read.working));
        assertTrue(CodeplugProject.equal(p.original,read.original));
        assertEquals(1,q.undo().model().zones.size());
        assertTrue(CodeplugProject.equal(p.original,q.reset().working));
        new CodeplugWritePlan(read,CodeplugWritePlan.allSections());
    }
    @Test public void emptyDtmfListAndDeletedGarbageSlotAreInitialized(){
        CodeplugSnapshot raw=fixture();Arrays.fill(raw.dtmfContacts,(byte)255);
        CodeplugProject p=new CodeplugProject(raw,project().identity);
        assertEquals(0,p.model().dtmfContacts.size());
        CodeplugProject q=add(p,DTMF,fields("name","Моя","code","12ab*#"));
        assertEquals(1,q.model().dtmfContacts.get(0).index);
        assertEquals("12AB*#",q.model().dtmfContacts.get(0).code);
        assertEquals(255,q.working.dtmfContacts[22]&255);
        new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
    }
    @Test public void markerBoundariesAndLastSlotUseCorrectOffsets(){
        for(int id:new int[]{8,9,127,128,129,256,257,896,897,1024}){
            CodeplugSnapshot raw=fixture();
            for(int i=1;i<=1024;i++){
                byte[] b=CodeplugProject.blocks(raw)[CodeplugRecords.block(CHANNEL,i)];
                int marker=CodeplugRecords.marker(CHANNEL,i),mask=1<<((i-1)%8);
                b[marker]=(byte)(i==id?b[marker]&~mask:b[marker]|mask);
            }
            assertEquals(id,CodeplugRecords.next(raw,CHANNEL));
            CodeplugProject p=new CodeplugProject(raw,project().identity);
            CodeplugProject q=add(p,CHANNEL,fields("name","Граница","rx","145.625","tx","145.025"));
            CodeplugWritePlan plan=new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
            int block=CodeplugRecords.block(CHANNEL,id),o=CodeplugRecords.offset(CHANNEL,id),marker=CodeplugRecords.marker(CHANNEL,id);
            byte[][] before=CodeplugProject.blocks(p.working),after=CodeplugProject.blocks(q.working);
            for(int b=0;b<before.length;b++)for(int j=0;j<before[b].length;j++)
                if(b!=block||(j!=marker&&(j<o||j>=o+56)))assertEquals(before[b][j],after[b][j]);
            assertTrue(plan.changes.containsKey(CodeplugWritePlan.ADDRESS[block]+marker));
        }
    }
    @Test public void lastContactsAndZoneAndCapacityLimits(){
        for(CodeplugRecords.Kind kind:new CodeplugRecords.Kind[]{DMR,DTMF,ZONE}){
            CodeplugSnapshot raw=fixture();int max=CodeplugRecords.limit(kind);
            for(int i=1;i<max;i++)CodeplugRecords.seed(raw,kind,i);
            assertEquals(max,CodeplugRecords.next(raw,kind));
            CodeplugProject p=new CodeplugProject(raw,project().identity);
            Map<String,String> f=kind==ZONE?fields("name","Последняя","members","1024"):kind==DMR?fields("name","Последний","number","16777215","type","2"):fields("name","Последний","code","*#");
            CodeplugProject q=add(p,kind,f);new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
            try{CodeplugRecords.next(q.working,kind);fail();}catch(IllegalArgumentException expected){}
        }
        CodeplugSnapshot raw=fixture();Arrays.fill(raw.channelBank0,0,16,(byte)255);
        for(int bank=0;bank<7;bank++)Arrays.fill(raw.channelBanks1to7,bank*0x1c10,bank*0x1c10+16,(byte)255);
        try{CodeplugRecords.next(raw,CHANNEL);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void allocationRespectsPhysicalMarkersEvenWithEmptyZoneNames(){
        CodeplugSnapshot raw=fixture();raw.zones[0]|=2;
        assertEquals(3,CodeplugRecords.next(raw,ZONE));
    }
    @Test public void occupiedAndInvalidCreationCannotMutateProject(){
        CodeplugProject p=project();
        try{p.edit(s->CodeplugRecords.create(s,CHANNEL,1,fields("name","X","rx","145.5","tx","145.5")));fail();}catch(IllegalArgumentException expected){}
        for(Map<String,String> f:Arrays.asList(fields("name","X"),fields("name","X","rx","145.123456","tx","145.5"),fields("name","🙂","rx","145.5","tx","145.5"))){
            try{add(p,CHANNEL,f);fail();}catch(IllegalArgumentException expected){}
            assertEquals(0,p.changedBytes());assertEquals(2,CodeplugRecords.next(p.working,CHANNEL));
        }
        try{add(p,DTMF,fields("name","DTMF","code","X"));fail();}catch(IllegalArgumentException expected){}
        try{add(p,ZONE,fields("name","Зона","members","999"));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void subsetCannotLeaveNewReferencesDangling(){
        CodeplugProject p=linked();
        for(boolean[] selection:new boolean[][]{
            {false,true,false,false,false,false,false,false},
            {false,false,false,false,true,false,false,false}}){
            try{new CodeplugWritePlan(p,selection);fail();}catch(IllegalArgumentException expected){}
        }
        CodeplugWritePlan contacts=new CodeplugWritePlan(p,new boolean[]{false,false,true,true,false,false,false,false});
        CodeplugProject next=contacts.completedProject();
        CodeplugWritePlan channels=new CodeplugWritePlan(next,new boolean[]{false,true,false,false,false,false,false,false});
        next=channels.completedProject();
        CodeplugWritePlan zones=new CodeplugWritePlan(next,new boolean[]{false,false,false,false,true,false,false,false});
        assertEquals(0,zones.completedProject().changedBytes());
    }
    @Test public void newRecordsWriteAndReadBackWithUnrelatedBytesPreserved()throws Exception{
        CodeplugProject p=linked();CodeplugWritePlan plan=new CodeplugWritePlan(p,CodeplugWritePlan.allSections());
        CodeplugWritePlanTest.Memory m=new CodeplugWritePlanTest.Memory(p);byte[] expected=m.data.clone();
        for(Map.Entry<Integer,Byte> e:plan.changes.entrySet())expected[e.getKey()]=e.getValue();
        plan.execute(m,sectors->{assertEquals(0,m.writes);m.backedUp=true;},message->{});
        assertArrayEquals(expected,m.data);assertEquals(0,plan.completedProject().changedBytes());
        byte[][] blocks=CodeplugProject.blocks(CodeplugProject.copy(p.original));
        for(int b=0;b<blocks.length;b++)blocks[b]=m.read(CodeplugWritePlan.ADDRESS[b],blocks[b].length);
        assertTrue(CodeplugProject.equal(p.working,CodeplugProject.fromBlocks(blocks)));
    }
    @Test public void staleReferencedChannelPreventsZoneWriteBeforeBackup()throws Exception{
        CodeplugProject p=add(project(),ZONE,fields("name","Зона","members","1"));
        CodeplugWritePlan plan=new CodeplugWritePlan(p,CodeplugWritePlan.allSections());
        CodeplugWritePlanTest.Memory m=new CodeplugWritePlanTest.Memory(p);m.data[0x3780+16]^=1;
        try{plan.execute(m,sectors->{m.backedUp=true;},message->{});fail();}catch(IOException expected){}
        assertEquals(0,m.writes);assertFalse(m.backedUp);
    }
    @Test public void malformedNewRecordsAndDeletionRemainBlocked(){
        CodeplugProject p=linked();
        for(int kind=0;kind<4;kind++){
            final int k=kind;
            CodeplugProject bad=p.edit(s->{
                if(k==0)s.channelBank0[16+56+0x32]=1;
                if(k==1)s.contacts[24+21]=1;
                if(k==2)s.channelBank0[0]&=~1;
                if(k==3)s.zones[31]|=4; // outside 250 usable slots
            });
            try{new CodeplugWritePlan(bad,CodeplugWritePlan.allSections());fail();}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void newChannelCanBeEditedAndSwitchModesBeforeFirstWrite(){
        CodeplugProject p=add(project(),CHANNEL,fields("name","Тест","rx","145.5","tx","145.5","rxTone","88.5","sql","5"));
        p=p.edit(s->CodeplugEditor.channel(s,2,fields("mode","1","contact","1","optionalId","4010151")));
        p=p.edit(s->CodeplugEditor.channel(s,2,fields("optionalId","0")));
        p=p.edit(s->CodeplugEditor.channel(s,2,fields("mode","0","sql","0")));
        new CodeplugWritePlan(p,CodeplugWritePlan.allSections());
    }
}
