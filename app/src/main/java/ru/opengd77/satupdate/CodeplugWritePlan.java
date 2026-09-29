package ru.opengd77.satupdate;

import java.io.IOException;
import java.util.*;

/** MD-9600 physical FLASH map. Only existing editor-supported records may change. */
final class CodeplugWritePlan {
    static final String[] NAMES={"DMR ID и позывной", "Каналы", "Контакты DMR", "Контакты DTMF", "Зоны"};
    static final int[][] BLOCKS={{1},{6,9},{10},{5},{8}};
    static final int[] ADDRESS={0x80,0xe0,0x1400,0x1588,0x1790,0x2f88,0x3780,0x7518,0x8010,0x9b1b0,0xa7620,0xad620,0x20000};
    final CodeplugProject project;
    final boolean[] selected;
    final SortedMap<Integer,Byte> changes=new TreeMap<>();
    final Set<Integer> blocks=new TreeSet<>();
    final int[] counts=new int[NAMES.length];

    CodeplugWritePlan(CodeplugProject project,boolean[] selected) {
        if(project==null||project.identity==null||project.identity.radioType!=5)
            throw new IllegalArgumentException("Для записи нужен проект, считанный с MD-9600");
        if(selected.length!=NAMES.length)throw new IllegalArgumentException("Неверный выбор разделов");
        this.project=project;this.selected=selected.clone();
        byte[][] a=CodeplugProject.blocks(project.original),b=CodeplugProject.blocks(project.working);
        byte[][] masks=CodeplugProject.blocks(CodeplugProject.copy(project.original));
        for(byte[] mask:masks)Arrays.fill(mask,(byte)0);
        Arrays.fill(masks[1],0,12,(byte)255);
        CodeplugModel original=OpenGd77CodeplugDecoder.decode(project.original);
        for(CodeplugModel.Channel c:original.channels){
            int bank=(c.index-1)/128,slot=(c.index-1)%128;
            int block=bank==0?6:9,o=(bank==0?0:(bank-1)*0x1c10)+16+slot*56;
            byte[] m=masks[block];Arrays.fill(m,o,o+0x1a,(byte)255);m[o+0x1b]=(byte)255;
            Arrays.fill(m,o+0x20,o+0x24,(byte)255);m[o+0x25]=(byte)0xc0;m[o+0x26]=(byte)0xe5;
            Arrays.fill(m,o+0x27,o+0x2a,(byte)255);Arrays.fill(m,o+0x2b,o+0x30,(byte)255);
            m[o+0x30]=15;m[o+0x31]=0x40;m[o+0x33]=0x77;m[o+0x36]=(byte)0xf0;m[o+0x37]=(byte)255;
        }
        for(CodeplugModel.Contact c:original.contacts){int o=(c.index-1)*24;Arrays.fill(masks[10],o,o+21,(byte)255);masks[10][o+23]=3;}
        for(CodeplugModel.DtmfContact c:original.dtmfContacts){int o=(c.index-1)*32;Arrays.fill(masks[5],o,o+32,(byte)255);}
        for(CodeplugModel.Zone z:original.zones){int o=32+(z.index-1)*176;Arrays.fill(masks[8],o,o+176,(byte)255);}
        // Imported projects are subject to the same address/bit boundaries as editor changes.
        for(int block=0;block<a.length;block++)for(int i=0;i<a[block].length;i++)
            if(((a[block][i]^b[block][i])&~masks[block][i]&255)!=0)
                throw new IllegalArgumentException("Проект содержит изменения вне поддерживаемых полей (блок "+block+"). Перечитайте рацию.");
        for(int section=0;section<NAMES.length;section++)for(int block:BLOCKS[section]){
            for(int i=0;i<a[block].length;i++)if(a[block][i]!=b[block][i]){
                counts[section]++;
                if(selected[section]){changes.put(ADDRESS[block]+i,b[block][i]);blocks.add(block);}
            }
        }
    }
    String summary(){StringBuilder s=new StringBuilder();for(int i=0;i<NAMES.length;i++)if(selected[i]&&counts[i]>0)s.append(NAMES[i]).append(": ").append(counts[i]).append(" байт\n");return s.toString();}
    CodeplugProject completedProject(){return project.acceptWritten(blocks);}

    interface Memory {
        byte[] read(int address,int length)throws IOException;
        void write(int address,byte[] data)throws IOException;
    }
    interface Backup { void save(List<Sector> sectors)throws IOException; }
    static final class Sector {
        final int address;final byte[] before,after;
        Sector(int address,byte[] before){this.address=address;this.before=before.clone();this.after=before.clone();}
    }
    void execute(Memory memory,Backup backup,RadioDriver.Progress progress)throws IOException {
        if(changes.isEmpty())throw new IOException("В выбранных разделах нет изменений");
        byte[][] source=CodeplugProject.blocks(project.original);
        Set<Integer> checks=new TreeSet<>(blocks);checks.add(1); // identity anchor even for contact-only writes
        for(int b:checks){
            progress.onMessage("Проверка исходных данных: 0x"+Integer.toHexString(ADDRESS[b]));
            if(!Arrays.equals(source[b],memory.read(ADDRESS[b],source[b].length)))
                throw new IOException("Данные в рации отличаются от исходного проекта (0x"+Integer.toHexString(ADDRESS[b])+"). Запись не начата. Сохраните проект и перечитайте рацию.");
        }
        SortedMap<Integer,Sector> sectors=new TreeMap<>();
        for(int address:changes.keySet()){
            int base=address&~4095;
            if(!sectors.containsKey(base)){
                byte[] read=memory.read(base,4096);if(read.length!=4096)throw new IOException("Неполный сектор FLASH");
                sectors.put(base,new Sector(base,read));
            }
        }
        // Check again against the exact sector image that will be preserved/written.
        for(Sector sector:sectors.values())for(int b:checks){
            int start=Math.max(sector.address,ADDRESS[b]),end=Math.min(sector.address+4096,ADDRESS[b]+source[b].length);
            for(int addr=start;addr<end;addr++)if(sector.before[addr-sector.address]!=source[b][addr-ADDRESS[b]])
                throw new IOException("Данные изменились во время проверки. Запись не начата.");
        }
        for(Map.Entry<Integer,Byte> e:changes.entrySet())sectors.get(e.getKey()&~4095).after[e.getKey()&4095]=e.getValue();
        backup.save(Collections.unmodifiableList(new ArrayList<>(sectors.values()))); // durable, before FIRST write
        int n=0;
        for(Sector sector:sectors.values()){
            progress.onMessage("Запись и проверка сектора "+(++n)+" / "+sectors.size()+" (0x"+Integer.toHexString(sector.address)+")");
            memory.write(sector.address,sector.after.clone());
            if(!Arrays.equals(sector.after,memory.read(sector.address,4096)))
                throw new IOException("Проверка записи не прошла: 0x"+Integer.toHexString(sector.address)+". Запись остановлена.");
        }
        // A final pass also detects changes to a previously verified sector.
        for(Sector sector:sectors.values())if(!Arrays.equals(sector.after,memory.read(sector.address,4096)))
            throw new IOException("Итоговая проверка FLASH не прошла: 0x"+Integer.toHexString(sector.address));
    }
}
