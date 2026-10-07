package ru.opengd77.satupdate;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static ru.opengd77.satupdate.CodeplugEditorTest.fields;
public class ChannelOrderTest {
    @Test public void moveAcrossWholeZoneAndBackPreservesIds(){
        List<Integer> order=new ArrayList<>();for(int i=1;i<=80;i++)order.add(i);
        ChannelOrder.move(order,Collections.emptySet(),0,79);assertEquals(Integer.valueOf(1),order.get(79));
        ChannelOrder.move(order,Collections.emptySet(),79,0);for(int i=1;i<=80;i++)assertEquals(Integer.valueOf(i),order.get(i-1));
    }
    @Test public void nonContiguousSelectionKeepsRelativeOrderMovingBothWays(){
        List<Integer> order=new ArrayList<>(Arrays.asList(1,128,129,1024));Set<Integer> selected=new HashSet<>(Arrays.asList(1,129));
        ChannelOrder.move(order,selected,0,3);assertEquals(Arrays.asList(128,1024,1,129),order);
        ChannelOrder.move(order,selected,2,0);assertEquals(Arrays.asList(1,129,128,1024),order);
        ChannelOrder.move(order,selected,0,1);assertEquals(Arrays.asList(1,129,128,1024),order);
    }
    @Test public void gestureOrderOnlyChangesZoneAndIsUndoable(){
        CodeplugProject p=ChannelBatchTest.clean().edit(s->CodeplugEditor.zone(s,1,fields("members","1 128 129 1024")));
        List<Integer> order=new ArrayList<>(p.model().zones.get(0).channelIndices);
        ChannelOrder.move(order,new HashSet<>(Arrays.asList(1,129)),0,3);
        CodeplugProject q=p.edit(s->CodeplugEditor.zone(s,1,fields("members",CodeplugRecords.join(order))));
        assertEquals(Arrays.asList(128,1024,1,129),q.model().zones.get(0).channelIndices);
        assertArrayEquals(p.working.channelBank0,q.working.channelBank0);assertArrayEquals(p.working.channelBanks1to7,q.working.channelBanks1to7);
        new CodeplugWritePlan(q,CodeplugWritePlan.allSections());assertTrue(CodeplugProject.equal(p.working,q.undo().working));
    }
    @Test public void multiDeleteCleansZoneLinksAndIsOneUndoStep(){
        CodeplugProject p=ChannelBatchTest.clean().edit(s->CodeplugEditor.zone(s,1,fields("members","1 128 129 1024")));
        CodeplugProject q=p.edit(s->{for(int id:new int[]{1,129})CodeplugRecords.delete(s,p.original,CodeplugRecords.Kind.CHANNEL,id);});
        assertEquals(Arrays.asList(128,1024),q.model().zones.get(0).channelIndices);assertEquals(2,q.model().channels.size());
        new CodeplugWritePlan(q,CodeplugWritePlan.allSections());assertTrue(CodeplugProject.equal(p.working,q.undo().working));
    }
}
