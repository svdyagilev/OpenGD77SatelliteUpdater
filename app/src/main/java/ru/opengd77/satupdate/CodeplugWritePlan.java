package ru.opengd77.satupdate;

import java.io.IOException;
import java.util.*;

/** MD-9600 physical FLASH map. Existing field patches plus validated new record allocation. */
final class CodeplugWritePlan {
    static final String[] NAMES={"DMR ID и позывной", "Каналы", "Контакты DMR", "Контакты DTMF", "Зоны", "Загрузочный экран", "Группы приёма", "APRS", "Списки сканирования", "Настройки DTMF", "Настройки рации", "VFO A/B", "Границы частот"};
    static final int[][] BLOCKS={{1},{6,9},{10,7},{5},{8},{7},{11},{3},{4},{2},{1},{7},{0}};
    static boolean[] allSections(){boolean[] selection=new boolean[NAMES.length];Arrays.fill(selection,true);return selection;}
    // Boot shares a snapshot block with VFOs. Cached VFO state may change on entry to CPS.
    static final int[] ADDRESS={0x80,0xe0,0x1400,0x1588,0x1790,0x2f88,0x3780,0x7518,0x8010,0x9b1b0,0xa7620,0xad620,0x20000};
    final CodeplugProject project;
    final boolean[] selected;
    final SortedMap<Integer,Byte> changes=new TreeMap<>();
    final Set<Integer> blocks=new TreeSet<>();
    final int[] counts=new int[NAMES.length];
    final Set<Integer> dependencies=new TreeSet<>();

    CodeplugWritePlan(CodeplugProject project,boolean[] selection) {
        if(project==null||project.identity==null||project.identity.radioType!=5)
            throw new IllegalArgumentException("Для записи нужен проект, считанный с MD-9600");
        if(selection.length>NAMES.length)throw new IllegalArgumentException("Неверный выбор разделов");
        this.project=project;this.selected=Arrays.copyOf(selection,NAMES.length);
        CodeplugIntegrity.masks(project);
        byte[][] a=CodeplugProject.blocks(project.original),b=CodeplugProject.blocks(project.working);
        CodeplugSnapshot effective=CodeplugProject.copy(project.original);byte[][] result=CodeplugProject.blocks(effective);
        for(int section=0;section<NAMES.length;section++)for(int block:BLOCKS[section])
            for(int i=0;i<a[block].length;i++)if(belongs(section,block,i)&&a[block][i]!=b[block][i]){
                counts[section]++;
                if(selected[section]){changes.put(ADDRESS[block]+i,b[block][i]);blocks.add(block);result[block][i]=b[block][i];}
            }
        CodeplugIntegrity.links(project.original,effective);
        if(blocks.contains(6)||blocks.contains(9)||selected[11]&&counts[11]>0){dependencies.add(10);dependencies.add(11);dependencies.add(3);}
        if(blocks.contains(8)||blocks.contains(4)){dependencies.add(6);dependencies.add(9);}
        if(blocks.contains(11))dependencies.add(10);
        checkVfos=selected[11]&&counts[11]>0;
        for(CodeplugRecords.Kind k:new CodeplugRecords.Kind[]{CodeplugRecords.Kind.DMR,CodeplugRecords.Kind.CHANNEL,CodeplugRecords.Kind.GROUP,CodeplugRecords.Kind.APRS})
            for(int id=1;id<=CodeplugRecords.limit(k);id++)if(CodeplugRecords.occupied(project.original,k,id)&&!CodeplugRecords.occupied(effective,k,id)){
                if(k==CodeplugRecords.Kind.CHANNEL){dependencies.add(8);dependencies.add(4);}
                else {dependencies.add(6);dependencies.add(9);dependencies.add(7);checkVfos=true;if(k==CodeplugRecords.Kind.DMR)dependencies.add(11);}
            }
    }
    private boolean checkVfos;
    private int checkLength(int block,byte[] data){return block==7&&!checkVfos?0x48:data.length;}
    static boolean belongs(int section,int block,int i){
        if(block==1)return section==0?i<12:i==19;
        if(block==7){
            if(section==2)return i>=12&&i<32; // DMR quick-key cleanup
            if(section==5)return i<12||i>=32&&i<0x48;
            if(section==11)return i>=0x78;
            return false;
        }
        return true;
    }
    String summary(){StringBuilder s=new StringBuilder();for(int i=0;i<NAMES.length;i++)if(selected[i]&&counts[i]>0)s.append(NAMES[i]).append(": ").append(counts[i]).append(" байт\n");return s.toString();}
    CodeplugSnapshot effectiveSnapshot(){
        CodeplugSnapshot effective=CodeplugProject.copy(project.original);byte[][] target=CodeplugProject.blocks(effective);
        for(Map.Entry<Integer,Byte> change:changes.entrySet())for(int block=0;block<target.length;block++){
            int offset=change.getKey()-ADDRESS[block];if(offset>=0&&offset<target[block].length){target[block][offset]=change.getValue();break;}
        }return effective;
    }
    CodeplugProject completedProject(){return project.acceptChanges(changes);}

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
        Set<Integer> checks=new TreeSet<>(blocks);checks.addAll(dependencies);checks.add(1);
        if(blocks.contains(11))checks.add(10); // group references must match the radio contacts
         // identity anchor even for contact-only writes
        for(int b:checks){
            progress.onMessage("Проверка исходных данных: 0x"+Integer.toHexString(ADDRESS[b]));
            if(!Arrays.equals(Arrays.copyOf(source[b],checkLength(b,source[b])),memory.read(ADDRESS[b],checkLength(b,source[b]))))
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
            int start=Math.max(sector.address,ADDRESS[b]),end=Math.min(sector.address+4096,ADDRESS[b]+checkLength(b,source[b]));
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
