package ru.opengd77.satupdate;

import org.junit.Test;
import static org.junit.Assert.*;
import static ru.opengd77.satupdate.CodeplugEditorTest.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class ProjectTransparencyTest {
    @Test public void readableDiffIncludesChannelFieldsAndRecordAddRemove(){
        CodeplugProject p=ChannelBatchTest.clean(),q=p.edit(s->{
            CodeplugEditor.channel(s,1,fields("power","9","rx","433.5"));ChannelBatch.copyZone(s,1,"Вторая");
        });
        ProjectDiff diff=new ProjectDiff(p.working,q.working);
        assertTrue(diff.bytes>0);assertTrue(diff.changes.stream().anyMatch(c->c.title.contains("Канал #1")&&c.detail.contains("433.5")&&c.detail.contains("Мощность")));
        assertTrue(diff.changes.stream().anyMatch(c->c.title.contains("Добавлено: Зона #2")));
        ProjectDiff reverse=new ProjectDiff(q.working,p.working);assertTrue(reverse.changes.stream().anyMatch(c->c.title.contains("Удалено: Зона #2")));
        assertEquals(0,new ProjectDiff(p.working,p.working).changes.size());
    }
    @Test public void writePreviewIncludesOnlySelectedFields(){
        CodeplugProject p=ChannelBatchTest.clean(),q=p.edit(s->{CodeplugEditor.channel(s,1,fields("power","9"));CodeplugEditor.general(s,fields("id","4019999"));});
        boolean[] selected=new boolean[CodeplugWritePlan.NAMES.length];selected[1]=true;
        CodeplugWritePlan plan=new CodeplugWritePlan(q,selected);CodeplugSnapshot effective=plan.effectiveSnapshot();
        assertEquals(4010151,OpenGd77CodeplugDecoder.decode(effective).general.dmrId);
        assertEquals(9,ChannelBatch.channel(effective,1).powerSetting);
        ProjectDiff diff=new ProjectDiff(q.original,effective);assertFalse(diff.changes.stream().anyMatch(c->c.title.equals("Радиостанция")));
        assertTrue(q.changedBytes()>plan.changes.size());
    }
    @Test public void auditDistinguishesNewBrokenLinksAndIntentionalWarnings(){
        CodeplugProject p=ChannelBatchTest.clean(),q=p.edit(s->{
            CodeplugRecords.create(s,CodeplugRecords.Kind.ZONE,2,fields("name","Пустая"));
            CodeplugRecords.create(s,CodeplugRecords.Kind.DMR,2,fields("name","Дубль","number",""+p.model().contacts.get(0).number,"type","1"));
        });
        ProjectCheck.Report check=ProjectCheck.inspect(q,q.working);assertTrue(check.errors.isEmpty());
        assertTrue(check.warnings.stream().anyMatch(x->x.contains("Пустая зона")));assertTrue(check.warnings.stream().anyMatch(x->x.contains("Повторяющийся")));
        CodeplugProject broken=p.edit(s->ByteUtil.putU16le(s.channelBank0,16+46,999));
        assertTrue(ProjectCheck.inspect(broken,broken.working).errors.stream().anyMatch(x->x.contains("#999")));
        CodeplugProject inherited=new CodeplugProject(broken.working,p.identity);
        assertTrue(ProjectCheck.inspect(inherited,inherited.working).warnings.stream().anyMatch(x->x.contains("при чтении")));
    }
    private byte[] backup(CodeplugProject before,CodeplugProject after)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            zip.putNextEntry(new ZipEntry("before.ogcproj"));zip.write(before.encode());zip.closeEntry();
            zip.putNextEntry(new ZipEntry("after.ogcproj"));zip.write(after.encode());zip.closeEntry();
        }return bytes.toByteArray();
    }
    @Test public void recoveryRestoresOnlyWrittenBytesAndKeepsUnsentEditsAndBaseline()throws Exception{
        CodeplugProject p=ChannelBatchTest.clean(),edited=p.edit(s->{CodeplugEditor.channel(s,1,fields("power","9"));CodeplugEditor.general(s,fields("id","4019999"));});
        boolean[] selected=new boolean[CodeplugWritePlan.NAMES.length];selected[1]=true;
        CodeplugProject after=new CodeplugWritePlan(edited,selected).completedProject();
        ProjectRecovery recovery=ProjectRecovery.read(new ByteArrayInputStream(backup(edited,after)));
        CodeplugProject restored=recovery.restore(after);
        assertEquals(8,ChannelBatch.channel(restored.working,1).powerSetting);assertEquals(4019999,restored.model().general.dmrId);
        assertTrue(CodeplugProject.equal(after.original,restored.original));assertTrue(CodeplugProject.equal(after.working,restored.undo().working));
        new CodeplugWritePlan(restored,CodeplugWritePlan.allSections());
    }
    @Test public void undoCreationViaBackupUsesValidatedDeletionAndProtectsLaterEdits()throws Exception{
        CodeplugProject p=ChannelBatchTest.clean(),added=p.edit(s->ChannelBatch.copyChannel(s,1,"Копия",Collections.emptyMap()));
        CodeplugProject after=new CodeplugWritePlan(added,CodeplugWritePlan.allSections()).completedProject();
        ProjectRecovery recovery=ProjectRecovery.read(new ByteArrayInputStream(backup(added,after)));
        CodeplugProject restored=recovery.restore(after);assertEquals(4,restored.model().channels.size());
        new CodeplugWritePlan(restored,CodeplugWritePlan.allSections());
        CodeplugProject later=after.edit(s->CodeplugEditor.channel(s,2,fields("power","9")));
        try{recovery.restore(later);fail();}catch(IllegalArgumentException expected){}
        assertEquals(9,ChannelBatch.channel(later.working,2).powerSetting);
    }
    @Test public void recoveryRejectsConflictingMultibyteFieldInsteadOfProducingHybridId()throws Exception{
        CodeplugProject p=ChannelBatchTest.clean(),edited=p.edit(s->CodeplugEditor.general(s,fields("id","4019999")));
        CodeplugProject after=new CodeplugWritePlan(edited,CodeplugWritePlan.allSections()).completedProject();
        ProjectRecovery recovery=ProjectRecovery.read(new ByteArrayInputStream(backup(edited,after)));
        CodeplugProject later=after.edit(s->CodeplugEditor.general(s,fields("id","5019999")));
        try{recovery.restore(later);fail();}catch(IllegalArgumentException expected){}
        assertEquals(5019999,later.model().general.dmrId);
    }
    @Test public void recoveryRejectsWrongFirmwareMissingProjectsAndCorruption()throws Exception{
        CodeplugProject p=ChannelBatchTest.clean(),edited=p.edit(s->CodeplugEditor.channel(s,1,fields("power","9")));
        CodeplugProject after=new CodeplugWritePlan(edited,CodeplugWritePlan.allSections()).completedProject();
        ProjectRecovery recovery=ProjectRecovery.read(new ByteArrayInputStream(backup(edited,after)));
        CodeplugProject wrong=new CodeplugProject(after.original,new RadioDriver.Identity("MD-9600",5,1,"OTHER"));
        try{recovery.restore(wrong);fail();}catch(IllegalArgumentException expected){}
        try{ProjectRecovery.read(new ByteArrayInputStream(new byte[0]));fail();}catch(IOException expected){}
        CodeplugProject inconsistent=p.edit(s->CodeplugEditor.general(s,fields("id","4010000")));
        try{ProjectRecovery.read(new ByteArrayInputStream(backup(edited,inconsistent)));fail();}catch(IOException expected){}
    }
    @Test public void versionHistorySurvivesReloadAndRetainsThirtyUniqueRevisions()throws Exception{
        File directory=new File(System.getProperty("java.io.tmpdir"),"opengd77-history-"+UUID.randomUUID());
        try{
            CodeplugProject p=ChannelBatchTest.clean();File first=ProjectVersions.save(directory,p.encode());
            assertEquals(first,ProjectVersions.save(directory,p.encode()));assertEquals(1,ProjectVersions.list(directory).length);
            for(int n=1;n<=35;n++){final int id=4010000+n;CodeplugProject q=p.edit(s->CodeplugEditor.general(s,fields("id",""+id)));ProjectVersions.save(directory,q.encode());}
            File[] history=ProjectVersions.list(directory);assertEquals(30,history.length);
            try(InputStream in=new FileInputStream(history[0])){assertEquals(4010035,CodeplugProject.read(in).model().general.dmrId);}
            try{ProjectVersions.save(directory,new byte[40]);fail();}catch(IOException expected){}
            assertEquals(30,ProjectVersions.list(directory).length);
        }finally{File[] files=directory.listFiles();if(files!=null)for(File file:files)file.delete();directory.delete();}
    }
}
