package ru.opengd77.satupdate;

import java.io.IOException;
import java.util.*;

/** MD-9600 physical FLASH map. Existing field patches plus validated new record allocation. */
final class CodeplugWritePlan {
    static final String[] NAMES={"DMR ID и позывной", "Каналы", "Контакты DMR", "Контакты DTMF", "Зоны", "Заставка / мелодия / спутники", "Группы приёма", "APRS", "Списки сканирования", "Настройки DTMF", "Настройки рации", "VFO A/B", "Границы частот"};
    static final int[][] BLOCKS={{1},{6,9},{10,7},{5},{8},{7,12},{11},{3},{4},{2},{1},{7},{0}};
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
    private boolean checksByte(int block,int offset){
        if(block!=7||offset<0x48)return true;
        // The gap contains no supported fields. VFO frequency/state may change on CPS entry.
        if(offset<0x78)return false;
        int vfo=0x78+((offset-0x78)/56)*56,local=(offset-0x78)%56;
        int start=local,end=local+1;
        if(local<16){start=0;end=16;}
        else if(local<24){start=16+((local-16)/4)*4;end=start+4;}
        else if(local>=32&&local<36){start=32+((local-32)/2)*2;end=start+2;}
        else if(local>=39&&local<42){start=39;end=42;}
        else if(local==46||local==47){start=46;end=48;}
        // Coordinates span three non-contiguous bytes; preserve them as one field.
        int[][] coordinates={{26,28,29},{30,31,36}};
        for(int[] field:coordinates)for(int member:field)if(local==member){for(int part:field)if(changes.containsKey(ADDRESS[7]+vfo+part))return true;}
        for(int part=start;part<end;part++)if(changes.containsKey(ADDRESS[7]+vfo+part))return true;
        // Deletion dependencies require stable references, not unrelated live VFO tuning.
        return checkVfos&&(local==43||local==45||local==46||local==47);
    }
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
            int length=checkLength(b,source[b]);byte[] current=memory.read(ADDRESS[b],length);
            if(current.length!=length)throw new IOException("Неполное чтение исходных данных");
            for(int i=0;i<length;i++)if(checksByte(b,i)&&source[b][i]!=current[i])
                throw new IOException("Данные в рации отличаются от исходного проекта (блок 0x"+Integer.toHexString(ADDRESS[b])+", адрес 0x"+Integer.toHexString(ADDRESS[b]+i)+", проект="+(source[b][i]&255)+", рация="+(current[i]&255)+"). Запись не начата. Сохраните проект, перечитайте рацию и повторите импорт/правки.");
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
            for(int addr=start;addr<end;addr++)if(checksByte(b,addr-ADDRESS[b])&&sector.before[addr-sector.address]!=source[b][addr-ADDRESS[b]])
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
