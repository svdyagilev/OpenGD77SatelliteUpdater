package ru.opengd77.satupdate;

import java.util.*;

/** Allocation uses physical slot markers, never list positions or list sizes. */
final class CodeplugRecords {
    enum Kind { DMR, DTMF, CHANNEL, ZONE }
    static int limit(Kind k){return k==Kind.DTMF?63:k==Kind.ZONE?250:1024;}
    static int block(Kind k,int index){return k==Kind.DMR?10:k==Kind.DTMF?5:k==Kind.ZONE?8:index<=128?6:9;}
    static int size(Kind k){return k==Kind.DMR?24:k==Kind.DTMF?32:k==Kind.ZONE?176:56;}
    static int offset(Kind k,int index){
        if(k==Kind.ZONE)return 32+(index-1)*176;
        if(k!=Kind.CHANNEL)return (index-1)*size(k);
        int bank=(index-1)/128;return (bank==0?0:(bank-1)*0x1c10)+16+(index-1)%128*56;
    }
    static int marker(Kind k,int index){
        if(k==Kind.ZONE)return (index-1)/8;
        int bank=(index-1)/128;return (bank==0?0:(bank-1)*0x1c10)+(index-1)%128/8;
    }
    static boolean occupied(CodeplugSnapshot s,Kind k,int index){
        if(index<1||index>limit(k))throw new IllegalArgumentException("Номер записи вне диапазона");
        byte[] b=CodeplugProject.blocks(s)[block(k,index)];
        if(k==Kind.CHANNEL||k==Kind.ZONE)return (b[marker(k,index)]&(1<<((index-1)%8)))!=0;
        int first=b[offset(k,index)]&255;return first!=0&&first!=255;
    }
    static int next(CodeplugSnapshot s,Kind k){
        for(int i=1;i<=limit(k);i++)if(!occupied(s,k,i))return i;
        throw new IllegalArgumentException("Нет свободных мест. Максимум записей: "+limit(k));
    }
    static void seed(CodeplugSnapshot s,Kind k,int index){
        byte[] b=CodeplugProject.blocks(s)[block(k,index)];int o=offset(k,index);
        Arrays.fill(b,o,o+size(k),(byte)0);
        CodeplugEditor.text(b,o,16,"Новая запись");
        if(k==Kind.CHANNEL||k==Kind.ZONE)b[marker(k,index)]|=1<<((index-1)%8);
        if(k==Kind.DTMF){Arrays.fill(b,o+16,o+32,(byte)255);b[o+16]=1;}
        if(k==Kind.DMR){ByteUtil.putU32be(b,o+16,1);b[o+23]=(byte)255;}
        if(k==Kind.CHANNEL){
            ByteUtil.putU32le(b,o+16,0x14550000);ByteUtil.putU32le(b,o+20,0x14550000);
            ByteUtil.putU16le(b,o+32,0xffff);ByteUtil.putU16le(b,o+34,0xffff);
            b[o+44]=1;b[o+54]=0x40; // CC1, 12.5 kHz tuning step; narrow FM, global power/squelch
        }
    }
    static void create(CodeplugSnapshot s,Kind k,int index,Map<String,String> fields){
        if(occupied(s,k,index))throw new IllegalArgumentException("Место уже занято. Откройте создание заново.");
        for(String key:k==Kind.CHANNEL?new String[]{"name","rx","tx"}:k==Kind.ZONE?new String[]{"name"}:new String[]{"name",k==Kind.DMR?"number":"code"})
            if(!fields.containsKey(key)||fields.get(key).trim().isEmpty())throw new IllegalArgumentException("Заполните имя и обязательные поля новой записи");
        seed(s,k,index);
        switch(k){
            case CHANNEL:CodeplugEditor.channel(s,index,fields);break;
            case ZONE:CodeplugEditor.zone(s,index,fields);break;
            default:CodeplugEditor.contact(s,index,k==Kind.DTMF,fields);
        }
    }
    static Object record(CodeplugModel m,Kind k,int index){
        switch(k){
            case CHANNEL:for(CodeplugModel.Channel c:m.channels)if(c.index==index)return c;break;
            case ZONE:for(CodeplugModel.Zone z:m.zones)if(z.index==index)return z;break;
            case DMR:for(CodeplugModel.Contact c:m.contacts)if(c.index==index)return c;break;
            case DTMF:for(CodeplugModel.DtmfContact c:m.dtmfContacts)if(c.index==index)return c;break;
        }
        throw new IllegalArgumentException("Некорректная новая запись #"+index);
    }

    private static Map<String,String> fields(Object... pairs){
        Map<String,String> out=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1] instanceof Boolean?((Boolean)pairs[i+1]?"1":"0"):String.valueOf(pairs[i+1]));
        return out;
    }
    private static String mhz(long hz){return java.math.BigDecimal.valueOf(hz,6).toPlainString();}
    /** Rebuild new records from validated values; rejects injected reserved bits and malformed BCD. */
    static void validateNew(CodeplugSnapshot s,CodeplugModel model,Kind k,int index){
        Object obj=record(model,k,index);
        CodeplugSnapshot rebuilt=CodeplugProject.copy(s);seed(rebuilt,k,index);
        if(k==Kind.CHANNEL){
            CodeplugModel.Channel c=(CodeplugModel.Channel)obj;
            CodeplugEditor.channel(rebuilt,index,fields("name",c.name,"rx",mhz(c.rxHz),"tx",mhz(c.txHz),
                "power",c.powerSetting,"tot",c.totSeconds,"step",c.stepIndex,"rxOnly",c.rxOnly,
                "beep",c.beepEnabled,"eco",c.ecoEnabled,"vox",c.vox,"zoneSkip",c.zoneSkip,
                "allSkip",c.allSkip,"fast",c.fastCall,"priority",c.priority,
                "rxTone",c.rxTone.displayText(),"txTone",c.txTone.displayText(),"wide",c.wide25k,
                "sql",c.squelchOverride?c.squelchLevel:0,"aprs",c.aprsConfigIndex));
            CodeplugEditor.channel(rebuilt,index,fields("mode","1","cc",c.colorCode,"ts",c.timeSlot,
                "contact",c.contactIndex,"group",c.rxGroupIndex,"optionalId",c.optionalDmrId,
                "dmo",c.forceDmo,"roaming",c.roaming,"ta1",c.taTxTs1,"ta2",c.taTxTs2));
            if(!c.digital)CodeplugEditor.channel(rebuilt,index,fields("mode","0"));
            // Values cached while their override is off are still legal editor output.
            int o=offset(k,index);byte[] raw=CodeplugProject.blocks(s)[block(k,index)];
            byte[] out=CodeplugProject.blocks(rebuilt)[block(k,index)];
            if(!c.squelchOverride){if(c.squelchLevel>21)throw new IllegalArgumentException("Некорректный шумоподавитель");out[o+55]=raw[o+55];}
            if(c.optionalDmrId==0)System.arraycopy(raw,o+39,out,o+39,3);
        }else if(k==Kind.DMR){
            CodeplugModel.Contact c=(CodeplugModel.Contact)obj;
            CodeplugEditor.contact(rebuilt,index,false,fields("name",c.name,"number",c.number,"type",c.type,"ts",c.tsOverride));
        }else if(k==Kind.DTMF){
            CodeplugModel.DtmfContact c=(CodeplugModel.DtmfContact)obj;
            CodeplugEditor.contact(rebuilt,index,true,fields("name",c.name,"code",c.code));
        }else{
            CodeplugModel.Zone z=(CodeplugModel.Zone)obj;StringBuilder ids=new StringBuilder();
            for(int id:z.channelIndices)ids.append(id).append(' ');
            CodeplugEditor.zone(rebuilt,index,fields("name",z.name,"members",ids.toString()));
        }
        int o=offset(k,index),b=block(k,index);
        if(!Arrays.equals(Arrays.copyOfRange(CodeplugProject.blocks(s)[b],o,o+size(k)),
                Arrays.copyOfRange(CodeplugProject.blocks(rebuilt)[b],o,o+size(k))))
            throw new IllegalArgumentException("Новая запись #"+index+" содержит неподдерживаемые значения");
    }
}
