package ru.opengd77.satupdate;

import java.util.*;

/** Allocation uses physical slot markers, never list positions or list sizes. */
final class CodeplugRecords {
    enum Kind { DMR, DTMF, CHANNEL, ZONE, GROUP, SCAN, APRS }
    static int limit(Kind k){return k==Kind.DTMF?63:k==Kind.ZONE?250:k==Kind.GROUP?76:k==Kind.SCAN?64:k==Kind.APRS?8:1024;}
    static int block(Kind k,int index){return k==Kind.DMR?10:k==Kind.DTMF?5:k==Kind.ZONE?8:k==Kind.GROUP?11:k==Kind.SCAN?4:k==Kind.APRS?3:index<=128?6:9;}
    static int size(Kind k){return k==Kind.DMR?24:k==Kind.DTMF?32:k==Kind.ZONE?176:k==Kind.GROUP?80:k==Kind.SCAN?88:k==Kind.APRS?64:56;}
    static int offset(Kind k,int index){
        if(k==Kind.ZONE)return 32+(index-1)*176;
        if(k==Kind.GROUP)return 128+(index-1)*80;
        if(k==Kind.SCAN)return 64+(index-1)*88;
        if(k!=Kind.CHANNEL)return (index-1)*size(k);
        int bank=(index-1)/128;return (bank==0?0:(bank-1)*0x1c10)+16+(index-1)%128*56;
    }
    static int marker(Kind k,int index){
        if(k==Kind.GROUP||k==Kind.SCAN)return index-1;
        if(k==Kind.ZONE)return (index-1)/8;
        int bank=(index-1)/128;return (bank==0?0:(bank-1)*0x1c10)+(index-1)%128/8;
    }
    static boolean occupied(CodeplugSnapshot s,Kind k,int index){
        if(index<1||index>limit(k))throw new IllegalArgumentException("Номер записи вне диапазона");
        byte[] b=CodeplugProject.blocks(s)[block(k,index)];
        if(k==Kind.CHANNEL||k==Kind.ZONE)return (b[marker(k,index)]&(1<<((index-1)%8)))!=0;
        if(k==Kind.GROUP||k==Kind.SCAN)return b[index-1]!=0;
        int first=b[offset(k,index)]&255;return first!=0&&first!=255;
    }
    static int next(CodeplugSnapshot s,Kind k){
        for(int i=1;i<=limit(k);i++)if(!occupied(s,k,i))return i;
        throw new IllegalArgumentException("Нет свободных мест. Максимум записей: "+limit(k));
    }
    static void seed(CodeplugSnapshot s,Kind k,int index){
        byte[] b=CodeplugProject.blocks(s)[block(k,index)];int o=offset(k,index);
        Arrays.fill(b,o,o+size(k),(byte)0);
        CodeplugEditor.text(b,o,k==Kind.APRS?8:k==Kind.GROUP||k==Kind.SCAN?15:16,k==Kind.APRS?"APRS":"Новая запись");
        if(k==Kind.GROUP){b[index-1]=1;b[o+15]=(byte)255;}
        if(k==Kind.SCAN){b[index-1]=1;b[o+15]=(byte)255;b[o+86]=40;b[o+87]=8;}
        if(k==Kind.APRS){b[o+29]='/';b[o+30]='>';b[o+62]='R';b[o+63]='A';}
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
        for(String key:k==Kind.CHANNEL?new String[]{"name","rx","tx"}:(k==Kind.ZONE||k==Kind.GROUP||k==Kind.SCAN||k==Kind.APRS)?new String[]{"name"}:new String[]{"name",k==Kind.DMR?"number":"code"})
            if(!fields.containsKey(key)||fields.get(key).trim().isEmpty())throw new IllegalArgumentException("Заполните имя и обязательные поля новой записи");
        seed(s,k,index);
        switch(k){
            case CHANNEL:CodeplugEditor.channel(s,index,fields);break;
            case ZONE:CodeplugEditor.zone(s,index,fields);break;
            case GROUP:CodeplugEditor.rxGroup(s,index,fields);break;
            case SCAN:CodeplugLists.scan(s,index,fields);break;
            case APRS:CodeplugEditor.aprs(s,index,fields);break;
            default:CodeplugEditor.contact(s,index,k==Kind.DTMF,fields);
        }
    }
    static Object record(CodeplugModel m,Kind k,int index){
        switch(k){
            case CHANNEL:for(CodeplugModel.Channel c:m.channels)if(c.index==index)return c;break;
            case ZONE:for(CodeplugModel.Zone z:m.zones)if(z.index==index)return z;break;
            case DMR:for(CodeplugModel.Contact c:m.contacts)if(c.index==index)return c;break;
            case DTMF:for(CodeplugModel.DtmfContact c:m.dtmfContacts)if(c.index==index)return c;break;
            case GROUP:for(CodeplugModel.RxGroup c:m.rxGroups)if(c.index==index)return c;break;
            case SCAN:for(CodeplugModel.ScanList c:m.scanLists)if(c.index==index)return c;break;
            case APRS:for(CodeplugModel.AprsConfig c:m.aprsConfigs)if(c.index==index)return c;break;
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
                "latitude",String.format(Locale.US,"%.4f",c.latitude),"longitude",String.format(Locale.US,"%.4f",c.longitude),"location",c.useLocation,
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
        }else if(k==Kind.GROUP){
            CodeplugModel.RxGroup g=(CodeplugModel.RxGroup)obj;
            CodeplugEditor.rxGroup(rebuilt,index,fields("name",g.name,"members",join(g.contactIndices)));
            if(s.rxGroups[index-1]!=rebuilt.rxGroups[index-1])throw new IllegalArgumentException("Неверная длина группы");
        }else if(k==Kind.SCAN){
            CodeplugModel.ScanList g=(CodeplugModel.ScanList)obj;
            CodeplugLists.scan(rebuilt,index,fields("name",g.name,"members",join(g.channelIndices),"primary",g.primary,"secondary",g.secondary,"revert",g.revert,
                "hold",(s.scanLists[offset(k,index)+86]&255)*25,"sample",(s.scanLists[offset(k,index)+87]&255)*250));
            if(s.scanLists[index-1]!=1)throw new IllegalArgumentException("Неверный маркер списка");
        }else if(k==Kind.APRS){
            CodeplugModel.AprsConfig a=(CodeplugModel.AprsConfig)obj;
            CodeplugEditor.aprs(rebuilt,index,fields("name",a.name,"ssid",a.senderSsid,"latitude",String.format(Locale.US,"%.4f",a.latitude),
                "longitude",String.format(Locale.US,"%.4f",a.longitude),"via1",a.via1,"via2",a.via2,"via1Ssid",a.via1Ssid,"via2Ssid",a.via2Ssid,
                "comment",a.comment,"tx",mhz(a.txHz),"baud300",(a.flags&1)!=0,"fixed",(a.flags&2)!=0,"qsy",(a.flags&4)!=0,
                "iconTable",Character.toString((char)a.iconTable),"icon",Character.toString((char)a.iconIndex)));
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

    static String join(List<Integer> ids){StringBuilder s=new StringBuilder();for(int id:ids)s.append(id).append(' ');return s.toString().trim();}
    static Kind kind(Object o){
        if(o instanceof CodeplugModel.Channel)return ((CodeplugModel.Channel)o).index>0?Kind.CHANNEL:null;
        if(o instanceof CodeplugModel.Zone)return Kind.ZONE;if(o instanceof CodeplugModel.Contact)return Kind.DMR;
        if(o instanceof CodeplugModel.DtmfContact)return Kind.DTMF;if(o instanceof CodeplugModel.RxGroup)return Kind.GROUP;
        if(o instanceof CodeplugModel.ScanList)return Kind.SCAN;if(o instanceof CodeplugModel.AprsConfig)return Kind.APRS;return null;
    }
    static int index(Object o){
        if(o instanceof CodeplugModel.Channel)return ((CodeplugModel.Channel)o).index;
        if(o instanceof CodeplugModel.Zone)return ((CodeplugModel.Zone)o).index;
        if(o instanceof CodeplugModel.Contact)return ((CodeplugModel.Contact)o).index;
        if(o instanceof CodeplugModel.DtmfContact)return ((CodeplugModel.DtmfContact)o).index;
        if(o instanceof CodeplugModel.RxGroup)return ((CodeplugModel.RxGroup)o).index;
        if(o instanceof CodeplugModel.ScanList)return ((CodeplugModel.ScanList)o).index;
        if(o instanceof CodeplugModel.AprsConfig)return ((CodeplugModel.AprsConfig)o).index;return 0;
    }
    static void tombstone(CodeplugSnapshot s,CodeplugSnapshot original,Kind k,int id){
        byte[] b=CodeplugProject.blocks(s)[block(k,id)],before=CodeplugProject.blocks(original)[block(k,id)];int o=offset(k,id);
        System.arraycopy(before,o,b,o,size(k));
        if(k==Kind.CHANNEL||k==Kind.ZONE)b[marker(k,id)]&=~(1<<((id-1)%8));
        else if(k==Kind.GROUP||k==Kind.SCAN)b[id-1]=0;
        else if(occupied(original,k,id))b[o]=(byte)255;
    }
    static void delete(CodeplugSnapshot s,CodeplugSnapshot original,Kind k,int id){
        if(!occupied(s,k,id))throw new IllegalArgumentException("Запись уже удалена");
        if(k==Kind.CHANNEL){
            for(int z=1;z<=250;z++)if(occupied(s,Kind.ZONE,z))removeWord(s.zones,offset(Kind.ZONE,z)+16,80,id);
            for(int scan=1;scan<=64;scan++)if(occupied(s,Kind.SCAN,scan)){
                int o=offset(Kind.SCAN,scan),wire=CodeplugLists.scanEncode(id);removeWord(s.scanLists,o+16,32,wire);
                for(int p=o+80;p<o+86;p+=2)if(ByteUtil.u16le(s.scanLists,p)==wire)ByteUtil.putU16le(s.scanLists,p,0);
            }
        }
        if(k==Kind.DMR||k==Kind.GROUP||k==Kind.APRS){
            for(int ch=1;ch<=1024;ch++)if(occupied(s,Kind.CHANNEL,ch))clearReference(CodeplugProject.blocks(s)[block(Kind.CHANNEL,ch)],offset(Kind.CHANNEL,ch),k,id);
            for(int v=0;v<2;v++)clearReference(s.bootAndVfos,0x78+56*v,k,id);
        }
        if(k==Kind.DMR){
            for(int g=1;g<=76;g++)if(occupied(s,Kind.GROUP,g)){
                int o=offset(Kind.GROUP,g);int count=(s.rxGroups[g-1]&255)-1;
                if(count<0||count>32)throw new IllegalArgumentException("Некорректная группа #"+g);
                boolean uses=false;for(int n=0;n<count;n++)uses|=ByteUtil.u16le(s.rxGroups,o+16+2*n)==id;
                if(uses)s.rxGroups[g-1]=(byte)(removeWord(s.rxGroups,o+16,32,id)+1);
            }
            for(int p=0x0c;p<0x20;p+=2)if(ByteUtil.u16le(s.bootAndVfos,p)==id)ByteUtil.putU16le(s.bootAndVfos,p,0x8000);
        }
        tombstone(s,original,k,id);
    }
    private static void clearReference(byte[] b,int o,Kind k,int id){
        if(k==Kind.DMR){if(ByteUtil.u16le(b,o+46)==id)ByteUtil.putU16le(b,o+46,0);}
        else {int p=o+(k==Kind.GROUP?43:45);if((b[p]&255)==id)b[p]=0;}
    }
    private static int removeWord(byte[] b,int o,int count,int id){
        boolean found=false;for(int i=0;i<count;i++)found|=ByteUtil.u16le(b,o+2*i)==id;
        if(!found){int n=0;for(int i=0;i<count;i++)if(ByteUtil.u16le(b,o+2*i)!=0)n++;return n;}
        int n=0;for(int i=0;i<count;i++){int ref=ByteUtil.u16le(b,o+2*i);if(ref!=0&&ref!=id)ByteUtil.putU16le(b,o+2*n++,ref);}
        Arrays.fill(b,o+2*n,o+2*count,(byte)0);return n;
    }
}
