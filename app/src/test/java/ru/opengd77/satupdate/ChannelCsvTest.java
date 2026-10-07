package ru.opengd77.satupdate;

import org.junit.Test;
import static org.junit.Assert.*;
import static ru.opengd77.satupdate.CodeplugEditorTest.*;
import java.io.*;
import java.util.*;

public class ChannelCsvTest {
    @Test public void utf8ExportImportPreservesEditableFmAndDmrSettings()throws Exception{
        CodeplugProject p=ChannelBatchTest.clean();
        p=p.edit(s->CodeplugEditor.channel(s,1,fields("name","Имя; \"Тест\"")));
        String csv=ChannelCsv.export(p.model());assertTrue(csv.contains("\"Имя; \"\"Тест\"\"\""));
        List<Map<String,String>> rows=ChannelCsv.read(new StringReader("\ufeff"+csv));
        CodeplugProject q=p.edit(s->ChannelCsv.append(s,rows));assertEquals(8,q.model().channels.size());
        CodeplugModel.Channel copy=ChannelBatch.channel(q.working,2),digital=ChannelBatch.channel(q.working,3);
        assertEquals("Имя; \"Тест\"",copy.name);assertEquals(145625000,copy.rxHz);assertEquals(145025000,copy.txHz);
        assertEquals(CodeplugEditor.tone("88.5"),copy.rxTone.raw);assertEquals(CodeplugEditor.tone("DCS 023 I"),copy.txTone.raw);
        assertTrue(digital.digital);assertEquals(3,digital.colorCode);assertEquals(2,digital.timeSlot);assertEquals(1,digital.contactIndex);
        new CodeplugWritePlan(q,CodeplugWritePlan.allSections());assertTrue(CodeplugProject.equal(p.working,q.undo().working));
    }
    @Test public void minimalCommaTableAndDecimalCommaInQuotedFrequencyWork()throws Exception{
        List<Map<String,String>> rows=ChannelCsv.read(new StringReader("name,rx_mhz,tx_mhz,mode\r\n\"ПМР\",\"446,00625\",446.00625,FM\r\n"));
        CodeplugProject q=ChannelBatchTest.clean().edit(s->ChannelCsv.append(s,rows));
        assertEquals(446006250,ChannelBatch.channel(q.working,2).rxHz);new CodeplugWritePlan(q,CodeplugWritePlan.allSections());
    }
    @Test public void badLastRowRejectsEntireImportWithoutAllocatingFirstRow()throws Exception{
        CodeplugProject p=ChannelBatchTest.clean();
        List<Map<String,String>> rows=ChannelCsv.read(new StringReader("name;rx_mhz;tx_mhz;mode\nOK;145.5;145.5;FM\nBad;invalid;145.5;FM\n"));
        try{p.edit(s->ChannelCsv.append(s,rows));fail();}catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("строка 3"));}
        assertEquals(0,p.changedBytes());assertEquals(4,p.model().channels.size());assertFalse(p.canUndo());
    }
    @Test public void invalidSchemaQuotesAndModesAreRejected()throws Exception{
        for(String csv:new String[]{"name;rx_mhz;tx_mhz;mode;name\nX;145.5;145.5;FM;X", "name;rx_mhz;tx_mhz;mode\n\"X;145.5;145.5;FM", "name;rx_mhz;tx_mhz;mode\nX;145.5;145.5;P25", "name;rx_mhz;mode\nX;145.5;FM", "name;rx_mhz;tx_mhz;mode\nX;145.5;145.5;FM;extra"}){
            try{ChannelCsv.read(new StringReader(csv));fail(csv);}catch(IOException expected){}
        }
    }
    @Test public void unresolvedReferencesRejectImport()throws Exception{
        List<Map<String,String>> rows=ChannelCsv.read(new StringReader("name;rx_mhz;tx_mhz;mode;contact_index\nX;433.5;433.5;DMR;999"));
        CodeplugProject p=ChannelBatchTest.clean();try{p.edit(s->ChannelCsv.append(s,rows));fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,p.changedBytes());
    }
}
