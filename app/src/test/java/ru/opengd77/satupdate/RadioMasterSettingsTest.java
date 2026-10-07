package ru.opengd77.satupdate;
import org.junit.Test;import static org.junit.Assert.*;
public class RadioMasterSettingsTest {
 @Test public void readsTheVersionedRusLayout(){byte[] b=new byte[64];ByteUtil.putU32le(b,0,RadioMasterSettings.VERSION);b[20]=8;b[45]=1;b[46]=8;b[47]=21;RadioMasterSettings s=new RadioMasterSettings(b);assertEquals(8,s.power);assertTrue(s.text().contains("40 Вт"));assertEquals("35%",RadioMasterSettings.sql(s.uhf));}
 @Test public void unknownVersionsAndValuesAreRejected(){byte[] b=new byte[64];try{new RadioMasterSettings(b);fail();}catch(IllegalArgumentException expected){}ByteUtil.putU32le(b,0,RadioMasterSettings.VERSION);b[20]=10;try{new RadioMasterSettings(b);fail();}catch(IllegalArgumentException expected){}}
}
