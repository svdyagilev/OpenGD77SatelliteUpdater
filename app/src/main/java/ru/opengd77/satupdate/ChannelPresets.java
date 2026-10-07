package ru.opengd77.satupdate;
import java.util.*;
/** Published channel plans. Frequencies are integer Hz; no floating-point rounding. */
final class ChannelPresets {
    static final String[] NAMES={"LPD433 — 69 каналов","PMR446 — 16 каналов","FRS — 22 канала"};
    static final String[] PREFIX={"LPD","PMR","FRS"};
    static final String[] SOURCES={
        "https://support.midlandeurope.com/media/manual/d/e/e/b/deeb04a16fe8979beba2952bde101bc86af7d5c9_Channels_G9_G9E_PLUS.pdf",
        "https://support.midlandeurope.com/media/manual/3/3/b/d/33bde692440e88d846d431ce4c696fafb3c13b85_channel_list_G11_PRO.pdf",
        "https://docs.fcc.gov/public/attachments/FCC-17-57A1_Rcd.pdf"};
    static long[] frequencies(int plan){
        if(plan<0||plan>2)throw new IllegalArgumentException("Неизвестный набор каналов");
        long[] hz=new long[plan==0?69:plan==1?16:22];
        for(int i=0;i<hz.length;i++)hz[i]=plan==0?433075000L+i*25000L:plan==1?446006250L+i*12500L:
            i<7?462562500L+i*25000L:i<14?467562500L+(i-7)*25000L:462550000L+(i-14)*25000L;
        return hz;
    }
    static String label(int plan,int n){return PREFIX[plan]+String.format(Locale.US,"%02d",n+1)+" · "+ChannelBatch.mhz(frequencies(plan)[n])+" МГц";}
    static void append(CodeplugSnapshot s,int plan,List<Integer> numbers,boolean skipDuplicates,boolean rxOnly,int power,int zone,String newZone){
        long[] hz=frequencies(plan);if(numbers.isEmpty())throw new IllegalArgumentException("Выберите каналы");
        List<Integer> members=new ArrayList<>();
        if(zone>0)members.addAll(((CodeplugModel.Zone)CodeplugRecords.record(OpenGd77CodeplugDecoder.decode(s),CodeplugRecords.Kind.ZONE,zone)).channelIndices);
        Set<Integer> seen=new HashSet<>();
        for(int n:numbers){
            if(n<0||n>=hz.length||!seen.add(n))throw new IllegalArgumentException("Неверный номер канала набора");
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Операция отменена");
            int id=0;
            if(skipDuplicates)for(CodeplugModel.Channel c:OpenGd77CodeplugDecoder.decode(s).channels)
                if(!c.digital&&c.rxHz==hz[n]&&c.txHz==hz[n]){id=c.index;break;}
            if(id==0){
                id=CodeplugRecords.next(s,CodeplugRecords.Kind.CHANNEL);Map<String,String> f=new LinkedHashMap<>();
                f.put("name",PREFIX[plan]+String.format(Locale.US,"%02d",n+1));f.put("rx",ChannelBatch.mhz(hz[n]));f.put("tx",f.get("rx"));
                f.put("mode","0");f.put("rxTone","нет");f.put("txTone","нет");f.put("wide",plan==0?"1":"0");
                f.put("power",""+power);f.put("rxOnly",rxOnly?"1":"0");
                CodeplugRecords.create(s,CodeplugRecords.Kind.CHANNEL,id,f);
            }
            if(!members.contains(id))members.add(id);
        }
        if(zone!=0){
            if(members.size()>80)throw new IllegalArgumentException("В зоне не более 80 каналов. Выберите другой состав или новую зону.");
            Map<String,String> f=new LinkedHashMap<>();f.put("members",CodeplugRecords.join(members));
            if(zone<0){f.put("name",newZone);CodeplugRecords.create(s,CodeplugRecords.Kind.ZONE,CodeplugRecords.next(s,CodeplugRecords.Kind.ZONE),f);}
            else CodeplugEditor.zone(s,zone,f);
        }
    }
}
