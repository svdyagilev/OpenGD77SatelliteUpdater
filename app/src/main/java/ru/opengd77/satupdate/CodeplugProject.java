package ru.opengd77.satupdate;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Immutable project revisions. Edits always operate on a private copy, never on the read image. */
final class CodeplugProject {
    interface Change { void apply(CodeplugSnapshot snapshot); }
    final CodeplugSnapshot original;
    final CodeplugSnapshot working;
    final RadioDriver.Identity identity;
    private final List<CodeplugSnapshot> undo;
    private static final int[] LENGTHS = {0x60,0x28,0x78,512,0x1640,2016,0x1c10,0xe8,0xac00,0xc470,0x6000,0x1840,8192};
    private static final int MAGIC = 0x4F474350; // OGCP, format 1; not a Windows .ogd file

    CodeplugProject(CodeplugSnapshot raw, RadioDriver.Identity identity) {
        this(copy(raw), copy(raw), identity, new ArrayList<>());
    }
    private CodeplugProject(CodeplugSnapshot original, CodeplugSnapshot working,
                            RadioDriver.Identity identity, List<CodeplugSnapshot> undo) {
        this.original=original; this.working=working; this.identity=identity; this.undo=undo;
        validate(original); validate(working);
        OpenGd77CodeplugDecoder.decode(working, identity);
    }
    CodeplugModel model() { return OpenGd77CodeplugDecoder.decode(working, identity); }
    CodeplugProject edit(Change change) {
        CodeplugSnapshot next=copy(working);
        change.apply(next);
        if (equal(working,next)) return this;
        List<CodeplugSnapshot> history=new ArrayList<>(undo);
        history.add(working);
        if(history.size()>20) history.remove(0);
        return new CodeplugProject(original,next,identity,history);
    }
    boolean canUndo() { return !undo.isEmpty(); }
    CodeplugProject undo() {
        if(!canUndo()) return this;
        List<CodeplugSnapshot> history=new ArrayList<>(undo);
        CodeplugSnapshot next=history.remove(history.size()-1);
        return new CodeplugProject(original,next,identity,history);
    }
    CodeplugProject reset() { return edit(s -> {
        byte[][] a=blocks(s), b=blocks(original);
        for(int i=0;i<a.length;i++) System.arraycopy(b[i],0,a[i],0,a[i].length);
    }); }
    int changedBytes() {
        byte[][] a=blocks(original),b=blocks(working); int count=0;
        for(int i=0;i<a.length;i++) for(int j=0;j<a[i].length;j++) if(a[i][j]!=b[i][j])count++;
        return count;
    }
    static byte[][] blocks(CodeplugSnapshot s) {
        return new byte[][]{s.deviceInfo,s.generalSettings,s.dtmfSettings,s.aprsConfigs,s.scanLists,
            s.dtmfContacts,s.channelBank0,s.bootAndVfos,s.zones,s.channelBanks1to7,s.contacts,s.rxGroups,s.additionalSettings};
    }
    static CodeplugSnapshot fromBlocks(byte[][] b) {
        return new CodeplugSnapshot(b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12]);
    }
    static CodeplugSnapshot copy(CodeplugSnapshot s) {
        byte[][] b=blocks(s); for(int i=0;i<b.length;i++)b[i]=b[i].clone(); return fromBlocks(b);
    }
    static boolean equal(CodeplugSnapshot a,CodeplugSnapshot b) {
        byte[][] aa=blocks(a),bb=blocks(b);for(int i=0;i<aa.length;i++)if(!Arrays.equals(aa[i],bb[i]))return false;return true;
    }
    private static void validate(CodeplugSnapshot s) {
        byte[][] b=blocks(s);for(int i=0;i<b.length;i++)
            if(b[i].length!=LENGTHS[i])throw new IllegalArgumentException("Неполный проект: блок "+i);
    }
    byte[] encode() throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(MAGIC);out.writeInt(1);
        out.writeUTF(identity==null?"":identity.model);
        out.writeLong(identity==null?0:identity.radioType);
        out.writeLong(identity==null?0:identity.infoVersion);
        out.writeUTF(identity==null?"":identity.firmware);
        for(CodeplugSnapshot s:new CodeplugSnapshot[]{original,working})
            for(byte[] b:blocks(s)){out.writeInt(b.length);out.write(b);}
        out.flush(); byte[] payload=bytes.toByteArray();out.write(digest(payload));out.flush();return bytes.toByteArray();
    }
    static CodeplugProject read(InputStream in) throws IOException {
        if(in==null)throw new IOException("Файл не открыт");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
        while((n=in.read(chunk))!=-1){if(bytes.size()+n>400000)throw new IOException("Слишком большой файл проекта");bytes.write(chunk,0,n);}
        byte[] all=bytes.toByteArray();if(all.length<40)throw new IOException("Неполный файл проекта");
        byte[] payload=Arrays.copyOf(all,all.length-32);
        if(!MessageDigest.isEqual(digest(payload),Arrays.copyOfRange(all,all.length-32,all.length)))
            throw new IOException("Контрольная сумма проекта не совпадает");
        DataInputStream d=new DataInputStream(new ByteArrayInputStream(payload));
        if(d.readInt()!=MAGIC || d.readInt()!=1)throw new IOException("Нужен проект .ogcproj версии 1, не файл Windows CPS");
        String model=d.readUTF();long type=d.readLong(),version=d.readLong();String firmware=d.readUTF();
        RadioDriver.Identity id=model.isEmpty()?null:new RadioDriver.Identity(model,type,version,firmware);
        CodeplugSnapshot[] ss=new CodeplugSnapshot[2];
        for(int k=0;k<2;k++){
            byte[][] b=new byte[LENGTHS.length][];
            for(int i=0;i<b.length;i++){if(d.readInt()!=LENGTHS[i])throw new IOException("Неверный размер блока проекта");b[i]=new byte[LENGTHS[i]];d.readFully(b[i]);}
            ss[k]=fromBlocks(b);
        }
        if(d.available()!=0)throw new IOException("Лишние данные в проекте");
        try{return new CodeplugProject(ss[0],ss[1],id,new ArrayList<>());}
        catch(IllegalArgumentException e){throw new IOException("Некорректный проект",e);}
    }
    private static byte[] digest(byte[] b) throws IOException {
        try{return MessageDigest.getInstance("SHA-256").digest(b);}
        catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
}
