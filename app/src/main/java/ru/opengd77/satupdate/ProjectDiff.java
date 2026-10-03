package ru.opengd77.satupdate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** Readable comparisons of working images; never changes either project. */
final class ProjectDiff {
    static final class Change {
        final String title,detail;
        Change(String title,String detail){this.title=title;this.detail=detail;}
    }
    final List<Change> changes=new ArrayList<>();
    final int bytes;
    private final boolean[][] covered;
    private static final Map<String,String> LABELS=new LinkedHashMap<>();
    static {
        String[] pairs={"name","Имя","radioName","Позывной станции","dmrId","DMR ID","voxSense","VOX", "rxHz","Приём","txHz","Передача",
            "digital","Режим DMR","powerSetting","Мощность","rxTone","Субтон приёма","txTone","Субтон передачи","colorCode","Color Code","timeSlot","Таймслот",
            "contactIndex","Контакт","rxGroupIndex","Группа приёма","optionalDmrId","DMR ID канала","rxOnly","Только приём","wide25k","Полоса 25 кГц",
            "beepEnabled","Звуки","ecoEnabled","Экономайзер","totSeconds","Ограничение передачи, с","stepIndex","Шаг частоты","vox","VOX",
            "zoneSkip","Пропуск сканирования зоны","allSkip","Пропуск всех каналов","aprsConfigIndex","APRS","forceDmo","DMO","roaming","Роуминг",
            "channelIndices","Каналы по порядку","contactIndices","Контакты по порядку","number","ID / TG","type","Тип контакта","tsOverride","TS контакта",
            "code","Код DTMF","introMode","Режим загрузочного экрана","line1","Строка 1","line2","Строка 2","primary","Приоритет 1","secondary","Приоритет 2","revert","Канал ответа",
            "holdMs","Удержание, мс","sampleMs","Интервал, мс","useLocation","Использовать координаты","latitude","Широта","longitude","Долгота","squelchOverride","Переопределение шумоподавителя",
            "squelchLevel","Шумоподавитель","fastCall","Быстрый вызов","priority","Приоритет сканирования","taTxTs1","Алиас TS1","taTxTs2","Алиас TS2",
            "senderSsid","SSID","via1","Путь 1","via2","Путь 2","via1Ssid","SSID пути 1","via2Ssid","SSID пути 2","comment","Комментарий","flags","Параметры",
            "selfId","Собственный ID DTMF","killCode","Код блокировки","wakeCode","Код разблокировки","pttUp","Код нажатия PTT","pttDown","Код отпускания PTT"};
        for(int i=0;i<pairs.length;i+=2)LABELS.put(pairs[i],pairs[i+1]);
    }
    ProjectDiff(CodeplugSnapshot before,CodeplugSnapshot after){
        byte[][] a=CodeplugProject.blocks(before),b=CodeplugProject.blocks(after);covered=new boolean[a.length][];for(int block=0;block<a.length;block++)covered[block]=new boolean[a[block].length];int count=0;
        for(int block=0;block<a.length;block++)count+=count(a[block],b[block],0,a[block].length);bytes=count;
        CodeplugModel old=OpenGd77CodeplugDecoder.decode(before),now=OpenGd77CodeplugDecoder.decode(after);
        for(CodeplugRecords.Kind kind:CodeplugRecords.Kind.values())for(int id=1;id<=CodeplugRecords.limit(kind);id++){
            boolean was=CodeplugRecords.occupied(before,kind,id),is=CodeplugRecords.occupied(after,kind,id);
            if(!was&&!is)continue;
            int block=CodeplugRecords.block(kind,id),offset=CodeplugRecords.offset(kind,id);
            int changed=count(a[block],b[block],offset,CodeplugRecords.size(kind));
            if(was==is&&changed==0)continue;
            Arrays.fill(covered[block],offset,offset+CodeplugRecords.size(kind),true);
            if(was!=is){if(kind==CodeplugRecords.Kind.CHANNEL||kind==CodeplugRecords.Kind.ZONE)covered[block][CodeplugRecords.marker(kind,id)]=true;
                else if(kind==CodeplugRecords.Kind.GROUP||kind==CodeplugRecords.Kind.SCAN)covered[block][id-1]=true;}
            Object left=was?record(old,kind,id):null,right=is?record(now,kind,id):null;
            String action=!was?"Добавлено":!is?"Удалено":"Изменено";
            String title=action+": "+kindName(kind)+" #"+id+name(right!=null?right:left);
            String detail=!was?describe(right):!is?describe(left):fields(left,right);
            if(detail.isEmpty())detail="Изменены дополнительные параметры записи.";
            changes.add(new Change(title,detail+"\nИзменено байтов записи: "+changed));
        }
        summary(a,b,1,0,a[1].length,"Радиостанция",fields(old.general,now.general));
        summary(a,b,0,0,a[0].length,"Сведения и ограничения частот",old.deviceInfo.vhfRangeText()+" → "+now.deviceInfo.vhfRangeText()+"\n"+old.deviceInfo.uhfRangeText()+" → "+now.deviceInfo.uhfRangeText());
        summary(a,b,2,0,a[2].length,"Настройки DTMF",fields(old.dtmfSettings,now.dtmfSettings));
        summary(a,b,7,0,0x78,"Загрузочный экран и команды",fields(old.boot,now.boot));
        for(int v=0;v<2;v++)summary(a,b,7,0x78+56*v,56,"VFO "+(v==0?"A":"B"),fields(old.vfos.get(v),now.vfos.get(v)));
        summary(a,b,12,0,a[12].length,"Спутники и дополнительные настройки","Спутников: "+old.satellites.size()+" → "+now.satellites.size());
        String[] blocks={"Сведения о станции","Общие настройки","DTMF","APRS","Сканирование","Контакты DTMF","Каналы 1–128","Загрузочный экран / VFO","Зоны","Каналы 129–1024","Контакты DMR","Группы приёма","Дополнительные настройки"};
        for(int block=0;block<a.length;block++){int unlisted=0;for(int i=0;i<a[block].length;i++)if(!covered[block][i]&&a[block][i]!=b[block][i])unlisted++;
            if(unlisted>0)changes.add(new Change("Другие данные: "+blocks[block],"Изменено "+unlisted+" байт вне перечисленных записей (например, свободные слоты или служебные данные)."));}
    }
    private static int count(byte[] a,byte[] b,int offset,int length){int n=0;for(int i=offset;i<offset+length;i++)if(a[i]!=b[i])n++;return n;}
    private void summary(byte[][] a,byte[][] b,int block,int off,int length,String title,String detail){
        int n=count(a[block],b[block],off,length);Arrays.fill(covered[block],off,off+length,true);if(n>0)changes.add(new Change(title,(detail.isEmpty()?"Изменены дополнительные параметры.":detail)+"\nИзменено байтов: "+n));
    }
    private static Object record(CodeplugModel model,CodeplugRecords.Kind kind,int id){try{return CodeplugRecords.record(model,kind,id);}catch(IllegalArgumentException e){return null;}}
    private static String name(Object object){Map<String,String> values=values(object);String name=values.get("Имя");return name==null?"":" · "+name;}
    private static String format(Object value,String key){
        if(value==null)return "—";if(value instanceof CodeplugModel.Tone)return ((CodeplugModel.Tone)value).displayText();
        if(value instanceof Boolean)return (Boolean)value?"да":"нет";
        if(key.endsWith("Hz")&&value instanceof Long)return ChannelBatch.mhz((Long)value)+" МГц";
        return value.toString();
    }
    private static Map<String,String> values(Object object){
        Map<String,String> result=new LinkedHashMap<>();if(object==null)return result;
        for(Field field:object.getClass().getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&LABELS.containsKey(field.getName())){
            try{
                String value=format(field.get(object),field.getName());
                if(object instanceof CodeplugModel.Channel&&field.getName().equals("powerSetting"))value=((CodeplugModel.Channel)object).powerText();
                if(object instanceof CodeplugModel.Channel&&field.getName().equals("stepIndex"))value=((CodeplugModel.Channel)object).stepText();
                if(object instanceof CodeplugModel.Contact&&field.getName().equals("type"))value=((CodeplugModel.Contact)object).typeText();
                result.put(LABELS.get(field.getName()),value);
            }catch(IllegalAccessException e){throw new IllegalStateException(e);}
        }return result;
    }
    private static String describe(Object object){StringBuilder out=new StringBuilder();for(Map.Entry<String,String> e:values(object).entrySet())out.append(e.getKey()).append(": ").append(e.getValue()).append('\n');return out.toString().trim();}
    private static String fields(Object before,Object after){
        Map<String,String> a=values(before),b=values(after);Set<String> keys=new LinkedHashSet<>(a.keySet());keys.addAll(b.keySet());StringBuilder out=new StringBuilder();
        for(String key:keys)if(!Objects.equals(a.get(key),b.get(key)))out.append(key).append(": ").append((a.containsKey(key)?a.get(key):"—")).append(" → ").append((b.containsKey(key)?b.get(key):"—")).append('\n');
        return out.toString().trim();
    }
    static String kindName(CodeplugRecords.Kind kind){switch(kind){case CHANNEL:return "Канал";case ZONE:return "Зона";case DMR:return "Контакт DMR";case DTMF:return "Контакт DTMF";case GROUP:return "Группа приёма";case SCAN:return "Список сканирования";default:return "APRS";}}
}
