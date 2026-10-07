package ru.opengd77.satupdate;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
import static ru.opengd77.satupdate.CodeplugEditorTest.*;
public class ChannelPresetsTest {
 @Test public void channelPlanEndpointsAndFrsOrdering(){
  assertEquals(69,ChannelPresets.frequencies(0).length);assertEquals(433075000L,ChannelPresets.frequencies(0)[0]);assertEquals(434775000L,ChannelPresets.frequencies(0)[68]);
  assertEquals(446006250L,ChannelPresets.frequencies(1)[0]);assertEquals(446193750L,ChannelPresets.frequencies(1)[15]);
  long[] f=ChannelPresets.frequencies(2);assertEquals(22,f.length);assertEquals(462562500L,f[0]);assertEquals(467562500L,f[7]);assertEquals(467712500L,f[13]);assertEquals(462550000L,f[14]);assertEquals(462725000L,f[21]);
 }
 @Test public void lpdSetCanFillOneZoneAndUndoDoesNotTouchOriginal(){
  CodeplugProject p=project();List<Integer> all=new ArrayList<>();for(int i=0;i<69;i++)all.add(i);
  CodeplugProject q=p.edit(s->ChannelPresets.append(s,0,all,true,true,1,-1,"LPD"));new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
  assertEquals(73,q.model().channels.size());assertEquals(69,q.model().zones.get(1).channelIndices.size());
  CodeplugModel.Channel c=q.model().channels.get(1);assertTrue(c.rxOnly);assertTrue(c.wide25k);assertEquals(1,c.powerSetting);assertEquals(CodeplugModel.Tone.Type.NONE,c.rxTone.type);
  assertTrue(CodeplugProject.equal(p.working,q.undo().working));
  CodeplugProject r=q.edit(s->ChannelPresets.append(s,0,all,true,false,0,0,""));assertSame(q,r);
 }
 @Test public void invalidSelectionAndFullZoneLeaveProjectUntouched(){
  CodeplugProject p=project();try{p.edit(s->ChannelPresets.append(s,1,Arrays.asList(0,16),false,false,0,-1,"PMR"));fail();}catch(IllegalArgumentException expected){}assertEquals(0,p.changedBytes());
  try{p.edit(s->ChannelPresets.append(s,2,Arrays.asList(1,1),false,false,0,0,""));fail();}catch(IllegalArgumentException expected){}assertEquals(0,p.changedBytes());
 }
}
