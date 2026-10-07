package ru.opengd77.satupdate;
import org.junit.Test;
import static org.junit.Assert.*;
import static ru.opengd77.satupdate.CodeplugEditorTest.fields;
import java.util.*;
public class ConfiguredSeriesTest {
    @Test public void freshSeriesHasExplicitSettingsAndFrequencyOffsetWithOneUndo(){
        CodeplugProject p=ChannelBatchTest.clean(),q=p.edit(s->ChannelBatch.configuredSeries(s,0,fields("name","Серия ","count","3","spacing","6.25","rx","433.5","tx","434.1","mode","1","cc","8","ts","2","contact","1","power","9","tot","45","step","4","beep","0","eco","0")));
        for(int n=0;n<3;n++){CodeplugModel.Channel c=ChannelBatch.channel(q.working,n+2);assertEquals(433500000+6250*n,c.rxHz);assertEquals(434100000+6250*n,c.txHz);assertTrue(c.digital);assertEquals(8,c.colorCode);assertEquals(2,c.timeSlot);assertEquals(9,c.powerSetting);assertFalse(c.beepEnabled);assertFalse(c.ecoEnabled);}
        new CodeplugWritePlan(q,CodeplugWritePlan.allSections());assertTrue(CodeplugProject.equal(p.working,q.undo().working));
    }
    @Test public void invalidLastChannelAndArbitraryStepNeverChangeBase(){
        CodeplugProject p=ChannelBatchTest.clean();for(String spacing:new String[]{"7.5","50"})try{p.edit(s->ChannelBatch.configuredSeries(s,0,fields("name","X","count","3","spacing",spacing,"rx","999.99","tx","999.99")));fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,p.changedBytes());assertEquals(4,p.model().channels.size());
    }
}
