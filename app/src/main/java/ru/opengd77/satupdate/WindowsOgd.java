package ru.opengd77.satupdate;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** The 128 KiB packed OpenGD77 RUS CPS file, NOT the MD-9600 physical flash image. */
final class WindowsOgd {
    static final int SIZE=0x20000,ZONE_COUNT=68,CUSTOM=0x1ee60,CUSTOM_SIZE=SIZE-CUSTOM;
    private static final int[] OFFSETS={0x80,0xe0,0x1400,0x1588,0x1790,0x2f88,0x3780,0x7518,0x8010,0xb1b0,0x17620,0x1d620,CUSTOM};
    static void validate(byte[] bytes)throws IOException {
        if(bytes.length!=SIZE)throw new IOException("Нужен .ogd OpenGD77 RUS размером 131072 байта (128 КиБ)");
        byte[] header="RUSSIAN".getBytes(StandardCharsets.US_ASCII);
        for(int i=0;i<header.length;i++)if(bytes[i]!=header[i])throw new IOException("Файл не является .ogd OpenGD77 RUS. .g77, прошивка и ZIP не поддерживаются этим импортом.");
        if(bytes[7]!=(byte)255)throw new IOException("Неподдерживаемый заголовок OGD");
    }
    static byte[] read(InputStream in)throws IOException {
        if(in==null)throw new IOException("Файл не открыт");ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
        while((n=in.read(chunk))!=-1){if(b.size()+n>SIZE)throw new IOException("Файл OGD слишком большой");b.write(chunk,0,n);}byte[] bytes=b.toByteArray();validate(bytes);return bytes;
    }
    static CodeplugSnapshot decode(byte[] bytes)throws IOException {
        validate(bytes);byte[][] b=new byte[CodeplugProject.LENGTHS.length][];
        for(int i=0;i<b.length;i++){b[i]=new byte[CodeplugProject.LENGTHS[i]];Arrays.fill(b[i],(byte)255);}
        Arrays.fill(b[8],(byte)0);
        for(int i=0;i<b.length;i++)System.arraycopy(bytes,OFFSETS[i],b[i],0,i==8?32+ZONE_COUNT*176:i==12?CUSTOM_SIZE:b[i].length);
        // Old CPS supported 16 channels/zone. Match its upgrade detection and preserve slot numbers.
        if((bytes[0x8010+81]&255)>4){
            byte[] old=b[8].clone();Arrays.fill(b[8],32,b[8].length,(byte)0);
            for(int i=0;i<ZONE_COUNT;i++)System.arraycopy(old,32+i*48,b[8],32+i*176,48);
        }
        b[8][8]&=15;Arrays.fill(b[8],9,32,(byte)0);
        CodeplugSnapshot s=CodeplugProject.fromBlocks(b);
        try{AdditionalData.entries(s.additionalSettings);OpenGd77CodeplugDecoder.decode(s);}catch(IllegalArgumentException e){throw new IOException("Повреждённые данные OGD: "+e.getMessage(),e);}return s;
    }
    static CodeplugProject importInto(CodeplugProject base,byte[] bytes)throws IOException {
        CodeplugSnapshot source=decode(bytes);CodeplugModel m=OpenGd77CodeplugDecoder.decode(source);
        CodeplugProject next=base.edit(target->{
            // Allocate all incoming markers first so reference validation can resolve forward links.
            for(CodeplugRecords.Kind k:CodeplugRecords.Kind.values())for(int id=1;id<=CodeplugRecords.limit(k);id++){
                boolean incoming=CodeplugRecords.occupied(source,k,id),existing=CodeplugRecords.occupied(target,k,id);
                if(existing&&!incoming)CodeplugRecords.delete(target,base.original,k,id);
                else if(incoming&&!existing)CodeplugRecords.seed(target,k,id);
            }
            for(CodeplugRecords.Kind k:CodeplugRecords.Kind.values())for(int id=1;id<=CodeplugRecords.limit(k);id++)if(CodeplugRecords.occupied(source,k,id)){
                CodeplugSnapshot normal=CodeplugRecords.normalized(source,m,k,id);int block=CodeplugRecords.block(k,id),off=CodeplugRecords.offset(k,id),size=CodeplugRecords.size(k);
                byte[] to=CodeplugProject.blocks(target)[block],from=CodeplugProject.blocks(normal)[block];
                // Existing records preserve reserved bytes/bits; fresh slots use the canonical encoding.
                boolean existed=CodeplugRecords.occupied(base.original,k,id);byte[] mask=new byte[to.length];
                if(k==CodeplugRecords.Kind.CHANNEL&&existed)CodeplugIntegrity.channelMask(mask,off);
                else if(k==CodeplugRecords.Kind.DMR&&existed){Arrays.fill(mask,off,off+21,(byte)255);mask[off+23]=3;}
                else if(k==CodeplugRecords.Kind.APRS&&existed){Arrays.fill(mask,off,off+59,(byte)255);mask[off+61]=7;}
                else Arrays.fill(mask,off,off+size,(byte)255);
                for(int i=off;i<off+size;i++)to[i]=(byte)((to[i]&~mask[i])|(from[i]&mask[i]));
                if(k==CodeplugRecords.Kind.GROUP||k==CodeplugRecords.Kind.SCAN)to[id-1]=from[id-1];
            }
            Map<String,String> g=new LinkedHashMap<>();g.put("name",m.general.radioName);g.put("id",""+m.general.dmrId);CodeplugEditor.general(target,g);
            if(m.general.voxSense>=1&&m.general.voxSense<=10)target.generalSettings[19]=(byte)m.general.voxSense;
            // Identity, hardware band limits and unrelated device settings stay with the receiving radio.
            byte[] mask=new byte[target.dtmfSettings.length];Arrays.fill(mask,0,44,(byte)255);mask[44]=(byte)0xe0;Arrays.fill(mask,48,78,(byte)255);Arrays.fill(mask,80,110,(byte)255);Arrays.fill(mask,112,119,(byte)255);
            for(int i=0;i<mask.length;i++)target.dtmfSettings[i]=(byte)((target.dtmfSettings[i]&~mask[i])|(source.dtmfSettings[i]&mask[i]));
            target.bootAndVfos[0]=source.bootAndVfos[0];System.arraycopy(source.bootAndVfos,0x28,target.bootAndVfos,0x28,32);
            for(int off:new int[]{0x78,0xb0}){byte[] cm=new byte[target.bootAndVfos.length];CodeplugIntegrity.channelMask(cm,off);for(int i=off;i<off+56;i++)target.bootAndVfos[i]=(byte)((target.bootAndVfos[i]&~cm[i])|(source.bootAndVfos[i]&cm[i]));}
            byte[] custom=AdditionalData.mergeKnown(target.additionalSettings,source.additionalSettings);System.arraycopy(custom,0,target.additionalSettings,0,custom.length);
        }).withWindowsTemplate(bytes);
        CodeplugIntegrity.masks(next);CodeplugIntegrity.links(base.original,next.working);return next;
    }
    static byte[] encode(CodeplugProject project)throws IOException {
        CodeplugSnapshot s=project.working;
        for(int id=ZONE_COUNT+1;id<=250;id++)if(CodeplugRecords.occupied(s,CodeplugRecords.Kind.ZONE,id))throw new IOException("OGD поддерживает зоны #1–68. В проекте занята зона #"+id+"; экспорт отменён без потери данных.");
        AdditionalData.entries(s.additionalSettings);
        for(int i=CUSTOM_SIZE;i<s.additionalSettings.length;i++)if((s.additionalSettings[i]&255)!=255)throw new IOException("Дополнительные данные не помещаются в OGD (4512 байт). Сохраните проект .ogcproj.");
        byte[] bytes=project.windowsTemplate==null?new byte[SIZE]:project.windowsTemplate.clone();if(project.windowsTemplate==null)Arrays.fill(bytes,(byte)255);
        byte[][] b=CodeplugProject.blocks(s);
        for(int i=0;i<b.length;i++)System.arraycopy(b[i],0,bytes,OFFSETS[i],i==8?32+ZONE_COUNT*176:i==12?CUSTOM_SIZE:b[i].length);
        System.arraycopy("RUSSIAN".getBytes(StandardCharsets.US_ASCII),0,bytes,0,7);bytes[7]=(byte)255;
        bytes[0x8010+8]&=15;Arrays.fill(bytes,0x8010+9,0x8010+32,(byte)0);
        validate(bytes);return bytes;
    }
}
