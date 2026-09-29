package ru.opengd77.satupdate;

import java.math.BigDecimal;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Patches only explicitly changed fields of existing records. No USB/write commands. */
final class CodeplugEditor {
    private static final Charset CP1251=Charset.forName("windows-1251");
    static long number(String value,long min,long max,String label){
        try{long n=Long.parseLong(value.trim());if(n<min||n>max)throw new NumberFormatException();return n;}
        catch(NumberFormatException e){throw new IllegalArgumentException(label+": допустимо "+min+"…"+max);}
    }
    static long frequency(String value){
        try{
            long hz=new BigDecimal(value.trim().replace(',','.')).multiply(new BigDecimal("1000000")).longValueExact();
            if(hz<1000000||hz>999999990||hz%10!=0)throw new ArithmeticException();return hz;
        }catch(RuntimeException e){throw new IllegalArgumentException("Частота: 1…999.999990 МГц, шаг 10 Гц");}
    }
    static long bcd(long n){long result=0;int shift=0;while(n>0){result|=(n%10)<<shift;n/=10;shift+=4;}return result;}
    static void text(byte[] b,int off,int len,String value){
        if(value.trim().isEmpty())throw new IllegalArgumentException("Название не должно быть пустым");
        byte[] data;
        try{ByteBuffer bb=CP1251.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            data=new byte[bb.remaining()];bb.get(data);}
        catch(CharacterCodingException e){throw new IllegalArgumentException("Название: допустимы символы Windows-1251, без эмодзи");}
        if(data.length>len)throw new IllegalArgumentException("Название: не более "+len+" символов Windows-1251");
        for(byte x:data)if((x&255)<32||(x&255)==127)throw new IllegalArgumentException("Недопустимый символ в названии");
        Arrays.fill(b,off,off+len,(byte)0xff);
        for(int i=0;i<data.length;i++)b[off+i]=(byte)((data[i]&255)==255?127:data[i]);
    }
    private static void flag(byte[] b,int off,int mask,boolean on){b[off]=(byte)((b[off]&~mask)|(on?mask:0));}
    private static boolean bool(String s){if(!s.equals("0")&&!s.equals("1"))throw new IllegalArgumentException("Неверный переключатель");return s.equals("1");}
    static int tone(String s){
        s=s.trim().toUpperCase(Locale.ROOT).replace("ГЦ","").replace("HZ","").trim();
        if(s.equals("НЕТ")||s.equals("NONE")||s.equals("0"))return 0xffff;
        if(s.startsWith("DCS"))s=s.substring(3).trim();else if(s.startsWith("D"))s=s.substring(1).trim();
        s=s.replace(" ","");
        if(s.matches("[0-7]{3}[NI]"))return Integer.parseInt(s.substring(0,3),16)|(s.endsWith("I")?0xc000:0x8000);
        if(s.startsWith("CTCSS"))s=s.substring(5);
        try{int tenths=new BigDecimal(s.replace(',','.')).movePointRight(1).intValueExact();
            if(tenths<670||tenths>2541)throw new ArithmeticException();return (int)bcd(tenths);}
        catch(RuntimeException e){throw new IllegalArgumentException("Субтон: нет, CTCSS 88.5 или DCS 023 N / DCS 023 I");}
    }
    static void general(CodeplugSnapshot s,Map<String,String> fields){
        for(Map.Entry<String,String> e:fields.entrySet())switch(e.getKey()){
            case "name":text(s.generalSettings,0,8,e.getValue());break;
            case "id":ByteUtil.putU32be(s.generalSettings,8,bcd(number(e.getValue(),1,16777215,"DMR ID")));break;
            default:throw new IllegalArgumentException("Неизвестное поле");
        }
    }
    static void channel(CodeplugSnapshot s,int index,Map<String,String> fields){
        CodeplugModel model=OpenGd77CodeplugDecoder.decode(s);
        CodeplugModel.Channel old=null;for(CodeplugModel.Channel c:model.channels)if(c.index==index)old=c;
        if(old==null)throw new IllegalArgumentException("Канал не найден");
        int bank=(index-1)/128,slot=(index-1)%128;
        byte[] b=bank==0?s.channelBank0:s.channelBanks1to7;
        int o=(bank==0?0:(bank-1)*0x1c10)+16+slot*0x38;
        boolean digital=fields.containsKey("mode")?bool(fields.get("mode")):old.digital;
        for(Map.Entry<String,String> e:fields.entrySet()){
            String k=e.getKey(),v=e.getValue();
            if(digital&&Arrays.asList("rxTone","txTone","wide","sql","aprs").contains(k))throw new IllegalArgumentException("Аналоговое поле в цифровом режиме");
            if(!digital&&Arrays.asList("cc","ts","contact","group","optionalId","dmo","roaming","ta1","ta2").contains(k))throw new IllegalArgumentException("Цифровое поле в аналоговом режиме");
            switch(k){
                case "name":text(b,o,16,v);break;
                case "rx":ByteUtil.putU32le(b,o+0x10,bcd(frequency(v)/10));break;
                case "tx":ByteUtil.putU32le(b,o+0x14,bcd(frequency(v)/10));break;
                case "mode":b[o+0x18]=(byte)(digital?1:0);break;
                case "power":b[o+0x19]=(byte)number(v,0,10,"Мощность");break;
                case "tot":long t=number(v,0,495,"Ограничение передачи");if(t%15!=0)throw new IllegalArgumentException("Ограничение передачи: шаг 15 секунд");b[o+0x1b]=(byte)(t/15);break;
                case "rxOnly":flag(b,o+0x33,4,bool(v));break;
                case "beep":flag(b,o+0x26,0x40,!bool(v));break;
                case "eco":flag(b,o+0x26,0x20,!bool(v));break;
                case "vox":flag(b,o+0x33,0x40,bool(v));break;
                case "zoneSkip":flag(b,o+0x33,0x20,bool(v));break;
                case "allSkip":flag(b,o+0x33,0x10,bool(v));break;
                case "fast":flag(b,o+0x25,0x80,bool(v));break;
                case "priority":flag(b,o+0x25,0x40,bool(v));break;
                case "step":b[o+0x36]=(byte)((b[o+0x36]&15)|((int)number(v,0,7,"Шаг частоты")<<4));break;
                case "rxTone":ByteUtil.putU16le(b,o+0x20,tone(v));break;
                case "txTone":ByteUtil.putU16le(b,o+0x22,tone(v));break;
                case "wide":flag(b,o+0x33,2,bool(v));break;
                case "sql":int sql=(int)number(v,0,21,"Шумоподавитель");flag(b,o+0x33,1,sql!=0);if(sql!=0)b[o+0x37]=(byte)sql;break;
                case "aprs":int aprs=(int)number(v,0,8,"APRS");if(aprs!=0&&!hasAprs(model,aprs))throw new IllegalArgumentException("Настройка APRS не найдена");b[o+0x2d]=(byte)aprs;break;
                case "cc":b[o+0x2c]=(byte)number(v,0,15,"Цветовой код");break;
                case "ts":flag(b,o+0x31,0x40,number(v,1,2,"Таймслот")==2);break;
                case "contact":int contact=(int)number(v,0,1024,"Контакт");if(contact!=0&&!hasContact(model,contact))throw new IllegalArgumentException("Контакт не найден");ByteUtil.putU16le(b,o+0x2e,contact);break;
                case "group":int group=(int)number(v,0,76,"Группа");if(group!=0&&!hasGroup(model,group))throw new IllegalArgumentException("Группа не найдена");b[o+0x2b]=(byte)group;break;
                case "optionalId":long id=number(v,0,16777215,"DMR ID канала");flag(b,o+0x26,0x80,id!=0);if(id!=0){b[o+0x27]=(byte)(id>>16);b[o+0x28]=(byte)(id>>8);b[o+0x29]=(byte)id;}break;
                case "dmo":flag(b,o+0x26,4,bool(v));break;
                case "roaming":flag(b,o+0x26,1,bool(v));break;
                case "ta1":b[o+0x30]=(byte)((b[o+0x30]&~3)|(int)number(v,0,3,"TA TS1"));break;
                case "ta2":b[o+0x30]=(byte)((b[o+0x30]&~12)|((int)number(v,0,3,"TA TS2")<<2));break;
                default:throw new IllegalArgumentException("Неизвестное поле канала: "+k);
            }
        }
        if(fields.containsKey("mode")&&digital!=old.digital){
            CodeplugModel.Channel now=null;for(CodeplugModel.Channel c:OpenGd77CodeplugDecoder.decode(s).channels)if(c.index==index)now=c;
            if(digital){
                if(now.colorCode>15)throw new IllegalArgumentException("Для цифрового режима укажите цветовой код 0…15");
                final int ci=now.contactIndex,gi=now.rxGroupIndex;
                if(ci!=0&&!hasContact(model,ci))throw new IllegalArgumentException("Выберите контакт для цифрового режима");
                if(gi!=0&&!hasGroup(model,gi))throw new IllegalArgumentException("Выберите группу для цифрового режима");
            }else{
                if(now.rxTone.type==CodeplugModel.Tone.Type.UNKNOWN||now.txTone.type==CodeplugModel.Tone.Type.UNKNOWN)throw new IllegalArgumentException("Для аналогового режима укажите корректные субтоны");
            }
        }
    }
    static void contact(CodeplugSnapshot s,int index,boolean dtmf,Map<String,String> fields){
        CodeplugModel m=OpenGd77CodeplugDecoder.decode(s);
        boolean exists=dtmf?hasDtmf(m,index):hasContact(m,index);
        if(!exists)throw new IllegalArgumentException("Контакт не найден");
        byte[] b=dtmf?s.dtmfContacts:s.contacts;int o=(index-1)*(dtmf?32:24);
        for(Map.Entry<String,String> e:fields.entrySet()){
            String v=e.getValue();switch(e.getKey()){
                case "name":text(b,o,16,v);break;
                case "code":if(!dtmf)throw new IllegalArgumentException("Неверный тип контакта");
                    v=v.toUpperCase(Locale.ROOT);if(!v.matches("[0-9ABCD*#]{1,16}"))throw new IllegalArgumentException("DTMF: 1–16 символов 0–9, A–D, *, #");
                    Arrays.fill(b,o+16,o+32,(byte)0xff);for(int i=0;i<v.length();i++)b[o+16+i]=(byte)"0123456789ABCD*#".indexOf(v.charAt(i));break;
                case "number":if(dtmf)throw new IllegalArgumentException("Неверный тип контакта");ByteUtil.putU32be(b,o+16,bcd(number(v,1,16777215,"ID/TG")));break;
                case "type":if(dtmf)throw new IllegalArgumentException("Неверный тип контакта");b[o+20]=(byte)number(v,0,2,"Тип контакта");break;
                case "ts":if(dtmf)throw new IllegalArgumentException("Неверный тип контакта");int ts=(int)number(v,0,3,"Таймслот");b[o+23]=(byte)((b[o+23]&~3)|ts);break;
                default:throw new IllegalArgumentException("Неизвестное поле контакта");
            }
        }
    }
    static void zone(CodeplugSnapshot s,int index,Map<String,String> fields){
        CodeplugModel m=OpenGd77CodeplugDecoder.decode(s);
        if(!hasZone(m,index))throw new IllegalArgumentException("Зона не найдена");
        int o=0x20+(index-1)*0xb0;
        for(Map.Entry<String,String> e:fields.entrySet())switch(e.getKey()){
            case "name":text(s.zones,o,16,e.getValue());break;
            case "members":
                String value=e.getValue().trim();String[] values=value.isEmpty()?new String[0]:value.split("[,;\\s]+");
                if(values.length>80)throw new IllegalArgumentException("В зоне не более 80 каналов");
                Set<Integer> seen=new HashSet<>();List<Integer> ids=new ArrayList<>();
                for(String v:values){int id=(int)number(v,1,1024,"Номер канала");
                    if(!seen.add(id))throw new IllegalArgumentException("Канал "+id+" повторяется");
                    if(!hasChannel(m,id))throw new IllegalArgumentException("Канал "+id+" не существует");ids.add(id);}
                Arrays.fill(s.zones,o+16,o+176,(byte)0);for(int i=0;i<ids.size();i++)ByteUtil.putU16le(s.zones,o+16+i*2,ids.get(i));break;
            default:throw new IllegalArgumentException("Неизвестное поле зоны");
        }
    }
    private static boolean hasContact(CodeplugModel m,int id){for(CodeplugModel.Contact c:m.contacts)if(c.index==id)return true;return false;}
    private static boolean hasDtmf(CodeplugModel m,int id){for(CodeplugModel.DtmfContact c:m.dtmfContacts)if(c.index==id)return true;return false;}
    private static boolean hasGroup(CodeplugModel m,int id){for(CodeplugModel.RxGroup g:m.rxGroups)if(g.index==id)return true;return false;}
    private static boolean hasAprs(CodeplugModel m,int id){for(CodeplugModel.AprsConfig a:m.aprsConfigs)if(a.index==id)return true;return false;}
    private static boolean hasZone(CodeplugModel m,int id){for(CodeplugModel.Zone z:m.zones)if(z.index==id)return true;return false;}
    private static boolean hasChannel(CodeplugModel m,int id){for(CodeplugModel.Channel c:m.channels)if(c.index==id)return true;return false;}
}
