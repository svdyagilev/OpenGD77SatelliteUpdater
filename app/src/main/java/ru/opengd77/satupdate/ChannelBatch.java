package ru.opengd77.satupdate;

import java.math.BigDecimal;
import java.util.*;

/** Channel operations on the project's private revision; no radio I/O. */
final class ChannelBatch {
    private ChannelBatch() {}
    static String mhz(long hz){return BigDecimal.valueOf(hz,6).toPlainString();}
    static CodeplugModel.Channel channel(CodeplugSnapshot s,int id){
        return (CodeplugModel.Channel)CodeplugRecords.record(OpenGd77CodeplugDecoder.decode(s),CodeplugRecords.Kind.CHANNEL,id);
    }
    static int copyChannel(CodeplugSnapshot s,int source,String name,Map<String,String> overrides){
        if(!CodeplugRecords.occupied(s,CodeplugRecords.Kind.CHANNEL,source))throw new IllegalArgumentException("Исходный канал не найден");
        int target=CodeplugRecords.next(s,CodeplugRecords.Kind.CHANNEL);
        byte[][] b=CodeplugProject.blocks(s);
        byte[] raw=Arrays.copyOfRange(b[CodeplugRecords.block(CodeplugRecords.Kind.CHANNEL,source)],
                CodeplugRecords.offset(CodeplugRecords.Kind.CHANNEL,source),CodeplugRecords.offset(CodeplugRecords.Kind.CHANNEL,source)+56);
        CodeplugRecords.seed(s,CodeplugRecords.Kind.CHANNEL,target);
        System.arraycopy(raw,0,b[CodeplugRecords.block(CodeplugRecords.Kind.CHANNEL,target)],CodeplugRecords.offset(CodeplugRecords.Kind.CHANNEL,target),56);
        Map<String,String> fields=new LinkedHashMap<>(overrides);fields.put("name",name);
        CodeplugEditor.channel(s,target,fields);
        // Never silently discard an original setting that the writer cannot encode.
        CodeplugRecords.validateNew(s,OpenGd77CodeplugDecoder.decode(s),CodeplugRecords.Kind.CHANNEL,target);
        return target;
    }
    static int copyZone(CodeplugSnapshot s,int source,String name){
        CodeplugModel.Zone zone=(CodeplugModel.Zone)CodeplugRecords.record(OpenGd77CodeplugDecoder.decode(s),CodeplugRecords.Kind.ZONE,source);
        int target=CodeplugRecords.next(s,CodeplugRecords.Kind.ZONE);
        Map<String,String> fields=new LinkedHashMap<>();fields.put("name",name);fields.put("members",CodeplugRecords.join(zone.channelIndices));
        CodeplugRecords.create(s,CodeplugRecords.Kind.ZONE,target,fields);return target;
    }
    static void series(CodeplugSnapshot s,int source,String prefix,int count,long rx,long tx,long step){
        if(count<1||count>1024)throw new IllegalArgumentException("Число каналов: 1…1024");
        if(step<0||step%10!=0)throw new IllegalArgumentException("Шаг должен быть неотрицательным и кратным 10 Гц");
        for(int i=0;i<count;i++){
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Операция отменена");
            Map<String,String> changes=new LinkedHashMap<>();
            changes.put("rx",mhz(Math.addExact(rx,Math.multiplyExact(step,i))));
            changes.put("tx",mhz(Math.addExact(tx,Math.multiplyExact(step,i))));
            copyChannel(s,source,prefix+(i+1),changes);
        }
    }
    static void configuredSeries(CodeplugSnapshot s,int source,Map<String,String> values){
        int count=(int)CodeplugEditor.number(values.get("count"),1,1024,"Количество каналов");
        String spacing=values.get("spacing");if(!Arrays.asList("0","2.5","5","6.25","10","12.5","25","30","50").contains(spacing))throw new IllegalArgumentException("Выберите шаг серии из списка");
        long step=new BigDecimal(spacing).multiply(BigDecimal.valueOf(1000)).longValueExact();
        long rx=CodeplugEditor.frequency(values.get("rx")),tx=CodeplugEditor.frequency(values.get("tx"));String name=values.get("name");
        if(name==null||name.trim().isEmpty())throw new IllegalArgumentException("Укажите имя серии");
        for(int n=0;n<count;n++){
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Операция отменена");
            Map<String,String> fields=new LinkedHashMap<>(values);fields.remove("count");fields.remove("spacing");
            fields.put("name",name+(n+1));fields.put("rx",mhz(Math.addExact(rx,Math.multiplyExact(step,n))));fields.put("tx",mhz(Math.addExact(tx,Math.multiplyExact(step,n))));
            if(source>0)copyChannel(s,source,fields.get("name"),fields);else CodeplugRecords.create(s,CodeplugRecords.Kind.CHANNEL,CodeplugRecords.next(s,CodeplugRecords.Kind.CHANNEL),fields);
        }
    }
    static void update(CodeplugSnapshot s,List<Integer> ids,Map<String,String> fields){
        if(ids.isEmpty()||fields.isEmpty())throw new IllegalArgumentException("Выберите каналы и хотя бы одно поле");
        for(String key:fields.keySet())if(!Arrays.asList("power","rxTone","txTone","cc","ts").contains(key))
            throw new IllegalArgumentException("Недопустимое массовое поле: "+key);
        Set<Integer> unique=new HashSet<>();
        for(int id:ids){
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Операция отменена");
            if(!unique.add(id))throw new IllegalArgumentException("Канал выбран дважды");
            CodeplugModel.Channel channel=channel(s,id);Map<String,String> patch=new LinkedHashMap<>(fields);
            if(channel.digital){patch.remove("rxTone");patch.remove("txTone");}
            else{patch.remove("cc");patch.remove("ts");}
            CodeplugEditor.channel(s,id,patch);
        }
    }
}
