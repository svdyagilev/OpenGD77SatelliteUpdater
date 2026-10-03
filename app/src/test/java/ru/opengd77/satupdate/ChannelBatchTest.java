package ru.opengd77.satupdate;

import org.junit.Test;
import static org.junit.Assert.*;
import static ru.opengd77.satupdate.CodeplugEditorTest.*;
import java.util.*;

public class ChannelBatchTest {
    static CodeplugProject clean(){
        CodeplugSnapshot s=fixture();
        for(int id:new int[]{1,128,129,1024}){
            CodeplugRecords.seed(s,CodeplugRecords.Kind.CHANNEL,id);
            CodeplugEditor.channel(s,id,fields("name","Канал "+id,"rx","145.625","tx","145.025","power","8"));
        }
        CodeplugEditor.channel(s,1,fields("rxTone","88.5","txTone","DCS 023 I"));
        CodeplugEditor.channel(s,128,fields("mode","1","contact","1","cc","3","ts","2"));
        return new CodeplugProject(s,project().identity);
    }
    @Test public void copyPreservesAllBytesExceptNameAndCanBeWrittenAndUndone(){
        CodeplugProject p=clean();CodeplugProject q=p.edit(s->ChannelBatch.copyChannel(s,1,"Копия",Collections.emptyMap()));
        assertEquals(5,q.model().channels.size());assertEquals(2,((CodeplugModel.Channel)CodeplugRecords.record(q.model(),CodeplugRecords.Kind.CHANNEL,2)).index);
        assertArrayEquals(Arrays.copyOfRange(p.working.channelBank0,32,72),Arrays.copyOfRange(q.working.channelBank0,88,128));
        new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
        assertTrue(CodeplugProject.equal(p.working,q.undo().working));assertTrue(CodeplugProject.equal(p.original,q.original));
    }
    @Test public void seriesUsesFreeSlotsAndRetainsRepeaterOffsetAndSettings(){
        CodeplugProject p=clean(),q=p.edit(s->ChannelBatch.series(s,128,"DMR-",3,433500000,434100000,12500));
        for(int n=0;n<3;n++){
            CodeplugModel.Channel c=(CodeplugModel.Channel)CodeplugRecords.record(q.model(),CodeplugRecords.Kind.CHANNEL,n+2);
            assertEquals("DMR-"+(n+1),c.name);assertEquals(433500000+n*12500,c.rxHz);assertEquals(600000,c.txHz-c.rxHz);
            assertTrue(c.digital);assertEquals(3,c.colorCode);assertEquals(2,c.timeSlot);assertEquals(1,c.contactIndex);
        }new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
    }
    @Test public void mixedBulkEditsOnlyApplicableFieldsAndIsOneUndoStep(){
        CodeplugProject p=clean();CodeplugProject q=p.edit(s->ChannelBatch.update(s,Arrays.asList(1,128),fields("power","9","rxTone","DCS 125 N","txTone","нет","cc","7","ts","1")));
        CodeplugModel.Channel fm=ChannelBatch.channel(q.working,1),dmr=ChannelBatch.channel(q.working,128);
        assertEquals(9,fm.powerSetting);assertEquals(9,dmr.powerSetting);assertFalse(fm.digital);assertTrue(dmr.digital);
        assertEquals(1,fm.colorCode);assertEquals(7,dmr.colorCode);assertEquals(1,dmr.timeSlot);
        assertEquals(CodeplugEditor.tone("DCS 125 N"),fm.rxTone.raw);assertEquals(0xffff,fm.txTone.raw);
        assertEquals(ChannelBatch.channel(p.working,128).rxTone.raw,dmr.rxTone.raw);
        new CodeplugWritePlan(q,CodeplugWritePlan.allSections());assertTrue(CodeplugProject.equal(p.working,q.undo().working));
    }
    @Test public void copyZoneAndReorderDoNotChangeSourceOrChannels(){
        CodeplugProject p=clean();CodeplugProject q=p.edit(s->{ChannelBatch.copyZone(s,1,"Зона-копия");CodeplugEditor.zone(s,2,fields("members","128 1 129"));});
        assertEquals(Arrays.asList(1),q.model().zones.get(0).channelIndices);assertEquals(Arrays.asList(128,1,129),q.model().zones.get(1).channelIndices);
        assertArrayEquals(p.working.channelBank0,q.working.channelBank0);new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
    }
    @Test public void failedSeriesAndBulkNeverMutateOriginalRevision(){
        CodeplugProject p=clean();
        for(CodeplugProject.Change change:Arrays.<CodeplugProject.Change>asList(
            s->ChannelBatch.series(s,1,"Слишком длинный префикс",2,145500000,145500000,12500),
            s->ChannelBatch.update(s,Arrays.asList(1,999),fields("power","9")),
            s->ChannelBatch.series(s,1,"X",2,999999990,999999990,10))){
            try{p.edit(change);fail();}catch(IllegalArgumentException expected){}
            assertEquals(0,p.changedBytes());assertEquals(4,p.model().channels.size());assertFalse(p.canUndo());
        }
    }
    @Test public void unsupportedTemplateIsRejectedRatherThanLosingSettings(){
        CodeplugProject p=project();try{p.edit(s->ChannelBatch.copyChannel(s,1,"X",Collections.emptyMap()));fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,p.changedBytes());assertFalse(p.canUndo());
    }
}
