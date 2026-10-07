package ru.opengd77.satupdate;
import org.junit.Test;import static org.junit.Assert.*;import static ru.opengd77.satupdate.CodeplugEditorTest.*;
import java.io.*;import java.util.*;
public class WindowsOgdTest {
 static CodeplugProject clean(){
  CodeplugSnapshot s=fixture();for(byte[] b:CodeplugProject.blocks(s))Arrays.fill(b,(byte)0);
  Arrays.fill(s.additionalSettings,(byte)255);CodeplugEditor.text(s.generalSettings,0,8,"UN6QCW");ByteUtil.putU32be(s.generalSettings,8,0x04010151L);
  s.generalSettings[19]=5;for(int off:new int[]{0x78,0xb0}){CodeplugEditor.text(s.bootAndVfos,off,16,"VFO");ByteUtil.putU32le(s.bootAndVfos,off+16,0x14550000);ByteUtil.putU32le(s.bootAndVfos,off+20,0x14550000);ByteUtil.putU16le(s.bootAndVfos,off+32,0xffff);ByteUtil.putU16le(s.bootAndVfos,off+34,0xffff);s.bootAndVfos[off+44]=1;}
  return new CodeplugProject(s,project().identity);
 }
 @Test public void mapRoundTripRussianNamesBanksAndUnknownGapBytes()throws Exception{
  CodeplugProject p=clean().edit(s->{for(int id:new int[]{1,128,129,1024})CodeplugRecords.create(s,CodeplugRecords.Kind.CHANNEL,id,fields("name","Моя "+id,"rx","433.5","tx","433.5"));CodeplugRecords.create(s,CodeplugRecords.Kind.ZONE,68,fields("name","Последняя","members","1024 129 1"));});
  byte[] bytes=WindowsOgd.encode(p);assertEquals(131072,bytes.length);bytes[0x7000]=42;
  CodeplugProject q=WindowsOgd.importInto(clean(),bytes);assertEquals(4,q.model().channels.size());assertEquals("Моя 129",q.model().channels.get(2).name);assertEquals(68,q.model().zones.get(0).index);
  assertEquals(Arrays.asList(1024,129,1),q.model().zones.get(0).channelIndices);assertEquals(42,WindowsOgd.encode(q)[0x7000]);new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
  CodeplugProject saved=CodeplugProject.read(new ByteArrayInputStream(q.encode()));assertArrayEquals(WindowsOgd.encode(q),WindowsOgd.encode(saved));assertTrue(CodeplugProject.equal(clean().working,q.undo().working));
 }
 @Test public void exportRefusesZonesThatCannotFitInsteadOfDroppingThem()throws Exception{
  CodeplugProject q=clean().edit(s->CodeplugRecords.create(s,CodeplugRecords.Kind.ZONE,69,fields("name","69","members","")));
  try{WindowsOgd.encode(q);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("69"));}
 }
 @Test public void corruptOrWrongFilesAndBadChannelAreAtomic()throws Exception{
  CodeplugProject p=clean();for(byte[] b:new byte[][]{new byte[1],new byte[WindowsOgd.SIZE],new byte[WindowsOgd.SIZE+1]}){try{WindowsOgd.read(new ByteArrayInputStream(b));fail();}catch(IOException expected){}}
  byte[] b=WindowsOgd.encode(p);b[0x3780]=1;b[0x3790+16]=(byte)0xfa;
  try{WindowsOgd.importInto(p,b);fail();}catch(IllegalArgumentException expected){}assertEquals(0,p.changedBytes());
 }
 @Test public void tlvRoundTripsAndUnknownRadioBlockCannotBeReplaced()throws Exception{
  CodeplugProject p=clean().edit(s->{byte[] image=BootImage.replace(s.additionalSettings,new byte[1024]);System.arraycopy(image,0,s.additionalSettings,0,image.length);});
  CodeplugProject q=WindowsOgd.importInto(clean(),WindowsOgd.encode(p));assertArrayEquals(BootImage.payload(p.working.additionalSettings),BootImage.payload(q.working.additionalSettings));new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
 }
 @Test public void legacyWindowsAprsSymbolsAreRetained()throws Exception{
  CodeplugProject p=clean().edit(s->{CodeplugRecords.seed(s,CodeplugRecords.Kind.APRS,1);s.aprsConfigs[29]=0;s.aprsConfigs[30]=15;});
  CodeplugProject q=WindowsOgd.importInto(clean(),WindowsOgd.encode(p));assertEquals(0,q.working.aprsConfigs[29]);assertEquals(15,q.working.aprsConfigs[30]);new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
 }
 @Test public void unknownTlvCannotBeChangedOrDeleted(){
  CodeplugProject p=clean().edit(s->{byte[] b=s.additionalSettings;System.arraycopy("OpenGD77".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,b,0,8);ByteUtil.putU32le(b,8,1);ByteUtil.putU32le(b,12,42);ByteUtil.putU32le(b,16,1);b[20]=7;});
  CodeplugProject q=p.edit(s->s.additionalSettings[20]=8);try{CodeplugIntegrity.masks(q);fail();}catch(IllegalArgumentException expected){}
  byte[] merged=AdditionalData.mergeKnown(p.working.additionalSettings,clean().working.additionalSettings);assertEquals(7,AdditionalData.entries(merged).get(42L)[0]);
 }
 @Test public void legacyProjectVersionOneStillOpens()throws Exception{
  byte[] bytes=clean().encode();java.io.DataOutputStream out;ByteArrayOutputStream raw=new ByteArrayOutputStream();byte[] payload=Arrays.copyOf(bytes,bytes.length-36);payload[7]=1;raw.write(payload);raw.write(java.security.MessageDigest.getInstance("SHA-256").digest(payload));assertEquals(0,CodeplugProject.read(new ByteArrayInputStream(raw.toByteArray())).changedBytes());
 }
}
