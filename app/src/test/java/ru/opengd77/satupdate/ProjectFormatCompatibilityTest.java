package ru.opengd77.satupdate;
import org.junit.Test;import static org.junit.Assert.*;import static ru.opengd77.satupdate.CodeplugEditorTest.*;
import java.io.*;import java.util.*;
public class ProjectFormatCompatibilityTest {
 static CodeplugProject clean(){
  CodeplugSnapshot s=fixture();for(byte[] b:CodeplugProject.blocks(s))Arrays.fill(b,(byte)0);
  Arrays.fill(s.additionalSettings,(byte)255);CodeplugEditor.text(s.generalSettings,0,8,"UN6QCW");ByteUtil.putU32be(s.generalSettings,8,0x04010151L);
  s.generalSettings[19]=5;for(int off:new int[]{0x78,0xb0}){CodeplugEditor.text(s.bootAndVfos,off,16,"VFO");ByteUtil.putU32le(s.bootAndVfos,off+16,0x14550000);ByteUtil.putU32le(s.bootAndVfos,off+20,0x14550000);ByteUtil.putU16le(s.bootAndVfos,off+32,0xffff);ByteUtil.putU16le(s.bootAndVfos,off+34,0xffff);s.bootAndVfos[off+44]=1;}
  return new CodeplugProject(s,project().identity);
 }
 @Test public void unknownTlvCannotBeChangedOrDeleted(){
  CodeplugProject p=clean().edit(s->{byte[] b=s.additionalSettings;System.arraycopy("OpenGD77".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,b,0,8);ByteUtil.putU32le(b,8,1);ByteUtil.putU32le(b,12,42);ByteUtil.putU32le(b,16,1);b[20]=7;});
  CodeplugProject q=p.edit(s->s.additionalSettings[20]=8);try{CodeplugIntegrity.masks(q);fail();}catch(IllegalArgumentException expected){}
  byte[] merged=AdditionalData.mergeKnown(p.working.additionalSettings,clean().working.additionalSettings);assertEquals(7,AdditionalData.entries(merged).get(42L)[0]);
 }
 @Test public void legacyProjectVersionOneStillOpens()throws Exception{
  byte[] bytes=clean().encode();java.io.DataOutputStream out;ByteArrayOutputStream raw=new ByteArrayOutputStream();byte[] payload=Arrays.copyOf(bytes,bytes.length-36);payload[7]=1;raw.write(payload);raw.write(java.security.MessageDigest.getInstance("SHA-256").digest(payload));assertEquals(0,CodeplugProject.read(new ByteArrayInputStream(raw.toByteArray())).changedBytes());
 }
 @Test public void versionTwoProjectWithFormerOgdTemplateStillRoundTrips()throws Exception{
  byte[] template=new byte[131072];Arrays.fill(template,(byte)255);
  System.arraycopy("RUSSIAN".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,template,0,7);template[0x7000]=42;
  CodeplugProject p=clean().withWindowsTemplate(template).edit(image->CodeplugEditor.general(image,fields("name","NEW")));
  CodeplugProject q=CodeplugProject.read(new ByteArrayInputStream(p.encode()));
  assertArrayEquals(template,q.windowsTemplate);assertArrayEquals(p.encode(),q.encode());
  assertTrue(CodeplugProject.equal(p.original,q.original));assertTrue(CodeplugProject.equal(p.working,q.working));
 }
}
