package ru.opengd77.satupdate;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class BootImageTest {
    private CodeplugProject base(){CodeplugProject p=ChannelBatchTest.clean();CodeplugSnapshot raw=CodeplugProject.copy(p.original);Arrays.fill(raw.additionalSettings,(byte)255);return new CodeplugProject(raw,p.identity);}
    @Test public void displayPackingHasVerticalPagesAndTransparencyIsWhite(){
        int[] pixels=new int[128*64];Arrays.fill(pixels,0xffffffff);pixels[0]=0xff000000;pixels[7*128+127]=0xff000000;pixels[8*128]=0xff000000;pixels[63*128+127]=0xff000000;pixels[1]=0x00000000;
        byte[] packed=BootImage.pack(pixels);assertEquals(1,packed[0]);assertEquals(0,packed[1]);assertEquals((byte)128,packed[127]);assertEquals(1,packed[128]);assertEquals((byte)128,packed[1023]);
    }
    @Test public void newImageSelectedWritePreservesOtherFlashAndUndo()throws Exception {
        CodeplugProject p=base();byte[] payload=new byte[1024];payload[0]=1;
        CodeplugProject q=p.edit(s->{byte[] added=BootImage.replace(s.additionalSettings,payload);System.arraycopy(added,0,s.additionalSettings,0,8192);s.bootAndVfos[0]=0;});
        boolean[] selected=new boolean[CodeplugWritePlan.NAMES.length];selected[5]=true;CodeplugWritePlan plan=new CodeplugWritePlan(q,selected);
        assertTrue(plan.changes.containsKey(0x20014));assertArrayEquals(payload,BootImage.payload(plan.effectiveSnapshot().additionalSettings));
        selected[5]=false;assertNull(BootImage.payload(new CodeplugWritePlan(q,selected).effectiveSnapshot().additionalSettings));
        byte[] flash=new byte[0x100000];Arrays.fill(flash,(byte)0xa5);byte[][] blocks=CodeplugProject.blocks(p.original);for(int i=0;i<blocks.length;i++)System.arraycopy(blocks[i],0,flash,CodeplugWritePlan.ADDRESS[i],blocks[i].length);
        byte[] expected=flash.clone();for(Map.Entry<Integer,Byte> entry:plan.changes.entrySet())expected[entry.getKey()]=entry.getValue();
        final int[] backups={0};plan.execute(new CodeplugWritePlan.Memory(){public byte[] read(int address,int length){return Arrays.copyOfRange(flash,address,address+length);}public void write(int address,byte[] data){assertEquals(1,backups[0]);System.arraycopy(data,0,flash,address,data.length);}},sectors->backups[0]++,message->{});
        assertArrayEquals(expected,flash);assertTrue(CodeplugProject.equal(p.working,q.undo().working));
        CodeplugProject reverted=plan.completedProject().edit(s->System.arraycopy(p.working.additionalSettings,0,s.additionalSettings,0,8192));new CodeplugWritePlan(reverted,CodeplugWritePlan.allSections());
    }
    @Test public void imageAppendPreservesMelodyAndSatelliteAndRejectsCorruption(){
        byte[] image=BootImage.replace(base().original.additionalSettings,new byte[1024]);int melody=12;ByteUtil.putU32le(image,melody,2);ByteUtil.putU32le(image,melody+4,1024);
        byte[] before=image.clone(),payload=new byte[1024];Arrays.fill(payload,(byte)0x55);byte[] after=BootImage.replace(before,payload);assertArrayEquals(Arrays.copyOfRange(before,12,1044),Arrays.copyOfRange(after,12,1044));assertArrayEquals(payload,BootImage.payload(after));
        ByteUtil.putU32le(image,16,9000);try{BootImage.replace(image,payload);fail();}catch(IllegalArgumentException expected){}
        CodeplugProject p=base();CodeplugProject bad=p.edit(s->{byte[] added=BootImage.replace(s.additionalSettings,payload);System.arraycopy(added,0,s.additionalSettings,0,8192);s.additionalSettings[7900]=1;});try{CodeplugIntegrity.masks(bad);fail();}catch(IllegalArgumentException expected){}
    }
}
