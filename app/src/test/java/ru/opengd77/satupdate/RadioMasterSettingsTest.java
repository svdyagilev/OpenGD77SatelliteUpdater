package ru.opengd77.satupdate;
import org.junit.Test;import static org.junit.Assert.*;
public class RadioMasterSettingsTest {
 private byte[] block(long magic){byte[] b=new byte[64];ByteUtil.putU32le(b,0,magic);b[20]=8;b[45]=1;b[46]=8;b[47]=21;return b;}
 @Test public void readsRusRadioSettingsWithoutMistakingMagicForWriteVersion(){for(long magic:new long[]{0x4761,0xdeadbeefL,0xdefece7eL}){RadioMasterSettings s=new RadioMasterSettings(block(magic));assertEquals(magic,s.magic);assertEquals(8,s.power);assertTrue(s.text().contains("40 Вт"));assertEquals("35%",RadioMasterSettings.sql(s.uhf));}}
 @Test public void shortBlocksAndInvalidFieldsAreRejected(){for(byte[] b:new byte[][]{new byte[63],new byte[65],new byte[64]}){try{new RadioMasterSettings(b);fail();}catch(IllegalArgumentException expected){}}byte[] b=block(0x4761);b[20]=10;try{new RadioMasterSettings(b);fail();}catch(IllegalArgumentException expected){}}
}
