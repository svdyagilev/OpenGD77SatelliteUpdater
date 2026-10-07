package ru.opengd77.satupdate;

import org.junit.Test;
import static org.junit.Assert.*;
import static ru.opengd77.satupdate.CodeplugEditorTest.*;
import java.io.*;
import java.util.*;

public class CodeplugWritePlanTest {
    static final boolean[] ALL=CodeplugWritePlan.allSections();
    static class Memory implements CodeplugWritePlan.Memory {
        byte[] data=new byte[0xb0000];int writes;boolean backedUp;int corrupt=-1,failAt=-1;
        Memory(CodeplugProject p){
            new Random(71).nextBytes(data);byte[][] blocks=CodeplugProject.blocks(p.original);
            for(int i=0;i<blocks.length;i++)System.arraycopy(blocks[i],0,data,CodeplugWritePlan.ADDRESS[i],blocks[i].length);
        }
        public byte[] read(int a,int len){return Arrays.copyOfRange(data,a,a+len);}
        public void write(int a,byte[] b)throws IOException{
            assertTrue("Backup required before first write",backedUp);writes++;
            if(writes==failAt)throw new IOException("disconnect");
            System.arraycopy(b,0,data,a,b.length);if(writes==corrupt)data[a]^=1;
        }
    }
    private void execute(CodeplugWritePlan p,Memory m)throws IOException{
        p.execute(m,sectors->{assertEquals(0,m.writes);assertFalse(sectors.isEmpty());
            for(CodeplugWritePlan.Sector s:sectors)assertArrayEquals(m.read(s.address,4096),s.before);
            m.backedUp=true;},text->{});
    }
    @Test public void multipleSectionsPreserveEveryUnrelatedByteIncludingSectorEdges()throws Exception{
        CodeplugProject p=project().edit(s->{
            for(int i:new int[]{1,128,129,1024})CodeplugEditor.channel(s,i,fields("rx","433.512500"));
            CodeplugEditor.general(s,fields("name","НОВЫЙ"));
            CodeplugEditor.contact(s,1,false,fields("number","25099"));
            CodeplugEditor.contact(s,1,true,fields("code","123AB"));
            CodeplugEditor.zone(s,1,fields("members","129,1"));
        });
        CodeplugWritePlan plan=new CodeplugWritePlan(p,ALL);Memory m=new Memory(p);byte[] expected=m.data.clone();
        for(Map.Entry<Integer,Byte> e:plan.changes.entrySet())expected[e.getKey()]=e.getValue();
        execute(plan,m);assertArrayEquals(expected,m.data);assertEquals(0,plan.completedProject().changedBytes());
        assertTrue(m.writes>1);
    }
    @Test public void lastSelectedBlockConflictPreventsALLWritesAndBackup()throws Exception{
        CodeplugProject p=project().edit(s->{CodeplugEditor.general(s,fields("name","TEST"));CodeplugEditor.contact(s,1,false,fields("number","123"));});
        Memory m=new Memory(p);m.data[0xa7620+10]^=1;
        try{execute(new CodeplugWritePlan(p,ALL),m);fail();}catch(IOException expected){}
        assertEquals(0,m.writes);assertFalse(m.backedUp);
    }
    @Test public void identityAnchorMismatchBlocksContactOnlyWrite()throws Exception{
        CodeplugProject p=project().edit(s->CodeplugEditor.contact(s,1,false,fields("name","TEST")));
        Memory m=new Memory(p);m.data[0xe0]^=1;
        try{execute(new CodeplugWritePlan(p,ALL),m);fail();}catch(IOException expected){}
        assertEquals(0,m.writes);
    }
    @Test public void subsetUpdatesBaselineAndRetainsUnselectedEdits()throws Exception{
        CodeplugProject p=project().edit(s->{CodeplugEditor.general(s,fields("name","TEST"));CodeplugEditor.channel(s,1,fields("rx","433.1"));});
        CodeplugWritePlan plan=new CodeplugWritePlan(p,new boolean[]{true,false,false,false,false,false,false,false});
        Memory m=new Memory(p);byte[] channels=m.read(0x3780,0x1c10);execute(plan,m);
        assertArrayEquals(channels,m.read(0x3780,0x1c10));
        CodeplugProject next=plan.completedProject();assertTrue(next.changedBytes()>0);assertFalse(next.canUndo());
        assertArrayEquals(next.working.generalSettings,next.original.generalSettings);
        assertArrayEquals(p.original.channelBank0,next.original.channelBank0);
        // A second session can write the remaining channel change against the updated baseline.
        execute(new CodeplugWritePlan(next,ALL),new Memory(next));
    }
    @Test public void backupFailurePreventsFirstWrite()throws Exception{
        CodeplugProject p=project().edit(s->CodeplugEditor.general(s,fields("name","TEST")));Memory m=new Memory(p);
        try{new CodeplugWritePlan(p,ALL).execute(m,sectors->{throw new IOException("disk full");},text->{});fail();}catch(IOException expected){}
        assertEquals(0,m.writes);
    }
    @Test public void verificationFailureStopsBeforeNextSector()throws Exception{
        CodeplugProject p=project().edit(s->{CodeplugEditor.general(s,fields("name","TEST"));CodeplugEditor.contact(s,1,false,fields("name","TEST"));});
        Memory m=new Memory(p);m.corrupt=1;
        try{execute(new CodeplugWritePlan(p,ALL),m);fail();}catch(IOException expected){}
        assertEquals(1,m.writes);assertTrue(p.changedBytes()>0);
    }
    @Test public void disconnectIsNeverRetried()throws Exception{
        CodeplugProject p=project().edit(s->{CodeplugEditor.general(s,fields("name","TEST"));CodeplugEditor.contact(s,1,false,fields("name","TEST"));});
        Memory m=new Memory(p);m.failAt=1;
        try{execute(new CodeplugWritePlan(p,ALL),m);fail();}catch(IOException expected){}
        assertEquals(1,m.writes);
    }
    @Test public void rawReservedFieldsAndNewRecordsCannotBeWritten(){
        for(int kind=0;kind<4;kind++){
            final int k=kind;CodeplugProject p=project().edit(s->{
                if(k==0)s.additionalSettings[0]^=1;
                if(k==1)s.channelBank0[0]^=2;
                if(k==2)s.contacts[21]^=1;
                if(k==3)s.channelBank0[16+0x26]^=2;
            });
            try{new CodeplugWritePlan(p,ALL);fail();}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void noChangesDoesNotReadOrWrite()throws Exception{
        CodeplugProject p=project();Memory m=new Memory(p);
        try{execute(new CodeplugWritePlan(p,ALL),m);fail();}catch(IOException expected){}
        assertEquals(0,m.writes);assertFalse(m.backedUp);
    }
    @Test public void changingSharedSectorInUnselectedSectionIsPreserved()throws Exception{
        CodeplugSnapshot raw=fixture();CodeplugEditor.text(raw.dtmfContacts,62*32,16,"LAST");
        Arrays.fill(raw.dtmfContacts,62*32+16,63*32,(byte)255);raw.dtmfContacts[62*32+16]=1;
        CodeplugProject p=new CodeplugProject(raw,project().identity).edit(s->{CodeplugEditor.contact(s,63,true,fields("name","TEST"));CodeplugEditor.channel(s,1,fields("name","NEW"));});
        CodeplugWritePlan plan=new CodeplugWritePlan(p,new boolean[]{false,false,false,true,false,false,false,false});Memory m=new Memory(p);
        m.data[0x3780+32]^=8;byte[] before=m.data.clone();
        for(Map.Entry<Integer,Byte> e:plan.changes.entrySet())before[e.getKey()]=e.getValue();
        execute(plan,m);assertArrayEquals(before,m.data);
    }
    @Test public void changingVfoReferencesPreservesUnrelatedLiveTuning()throws Exception{
        CodeplugProject p=ProjectFormatCompatibilityTest.clean().edit(s->CodeplugLists.vfo(s,0,fields("name","NEW")));
        CodeplugWritePlan plan=new CodeplugWritePlan(p,ALL);Memory m=new Memory(p);
        m.data[0x7518+0x78+16]^=1;m.data[0x7518+0x50]^=1;byte[] expected=m.data.clone();
        for(Map.Entry<Integer,Byte> e:plan.changes.entrySet())expected[e.getKey()]=e.getValue();
        execute(plan,m);assertArrayEquals(expected,m.data);
    }
    @Test public void editedVfoFieldConflictStillBlocksBeforeBackup()throws Exception{
        CodeplugProject p=ProjectFormatCompatibilityTest.clean().edit(s->CodeplugLists.vfo(s,0,fields("rx","433.5")));
        CodeplugWritePlan plan=new CodeplugWritePlan(p,ALL);Memory m=new Memory(p);
        int address=plan.changes.firstKey();m.data[address]^=1;
        try{execute(plan,m);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("адрес 0x"));}
        assertEquals(0,m.writes);assertFalse(m.backedUp);
    }
    @Test public void vfoReferenceDependencyStillBlocksContactDeletion()throws Exception{
        CodeplugProject base=ProjectFormatCompatibilityTest.clean().edit(s->CodeplugRecords.create(s,CodeplugRecords.Kind.DMR,1,fields("name","TG","number","123")));
        base=new CodeplugProject(base.working,base.identity);
        CodeplugProject p=base.edit(s->CodeplugRecords.delete(s,s,CodeplugRecords.Kind.DMR,1));
        Memory m=new Memory(p);m.data[0x7518+0x78+46]=1;
        try{execute(new CodeplugWritePlan(p,ALL),m);fail();}catch(IOException expected){}
        assertEquals(0,m.writes);assertFalse(m.backedUp);
    }
    @Test public void unchangedByteOfEditedVfoFrequencyCannotProduceHybridValue()throws Exception{
        CodeplugProject p=ProjectFormatCompatibilityTest.clean().edit(s->CodeplugLists.vfo(s,0,fields("rx","433.5")));
        CodeplugWritePlan plan=new CodeplugWritePlan(p,ALL);Memory m=new Memory(p);
        int address=0x7518+0x78+16;assertFalse(plan.changes.containsKey(address));m.data[address]^=1;
        try{execute(plan,m);fail();}catch(IOException expected){}
        assertEquals(0,m.writes);assertFalse(m.backedUp);
    }
}
