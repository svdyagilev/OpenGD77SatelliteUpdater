package ru.opengd77.satupdate;

import org.junit.Test;
import static org.junit.Assert.*;
import static ru.opengd77.satupdate.CodeplugEditorTest.*;
import java.util.*;

public class ToneChoicesTest {
    @Test public void everyTableEntryEncodesAndDecodesWithoutLosingCodeOrPolarity(){
        assertEquals(50,ToneChoices.values(1).length);
        assertEquals(83,ToneChoices.values(2).length);
        assertEquals(83,ToneChoices.values(3).length);
        Set<Integer> seen=new HashSet<>();
        for(int kind=0;kind<4;kind++)for(String value:ToneChoices.values(kind)){
            int raw=CodeplugEditor.tone(value);assertTrue(seen.add(raw));
            assertEquals(value,OpenGd77CodeplugDecoder.decodeTone(raw).displayText());
        }
        assertEquals(0x8023,CodeplugEditor.tone(ToneChoices.values(2)[0]));
        assertEquals(0xc754,CodeplugEditor.tone(ToneChoices.values(3)[82]));
    }
    @Test public void allCallAutomaticallyUsesBroadcastIdWhenTypeOrNumberChanges(){
        CodeplugProject p=project().edit(s->CodeplugEditor.contact(s,1,false,fields("type","2")));
        assertEquals(16777215,p.model().contacts.get(0).number);
        assertEquals(2,p.model().contacts.get(0).type);
        assertArrayEquals(new byte[]{0x16,0x77,0x72,0x15},Arrays.copyOfRange(p.working.contacts,16,20));
        p=p.edit(s->CodeplugEditor.contact(s,1,false,fields("number","25099")));
        assertEquals(16777215,p.model().contacts.get(0).number);
        p=p.edit(s->CodeplugEditor.contact(s,1,false,fields("type","0","number","25099")));
        assertEquals(25099,p.model().contacts.get(0).number);
        CodeplugProject created=p.edit(s->CodeplugRecords.create(s,CodeplugRecords.Kind.DMR,2,fields("name","Общий","type","2","number","1")));
        assertEquals(16777215,created.model().contacts.get(1).number);
        new CodeplugWritePlan(created,CodeplugWritePlan.allSections());
    }
}
