package ru.opengd77.satupdate;

import java.math.BigDecimal;
import java.util.*;

/** Encoders for documented fixed settings and variable lists. */
final class CodeplugLists {
    static List<Integer> members(String text,int max,int limit,boolean selected){
        List<Integer> out=new ArrayList<>();String value=text.trim();
        if(!value.isEmpty())for(String token:value.split("[,;\\s]+")){
            int id=(int)CodeplugEditor.number(token,selected?-1:1,limit,"Номер записи");
            if(id==0||out.contains(id))throw new IllegalArgumentException("Нулевой или повторяющийся номер записи");out.add(id);
        }
        if(out.size()>max)throw new IllegalArgumentException("В списке не более "+max+" записей");return out;
    }
    static boolean channelExists(CodeplugSnapshot s,int id){return id>0&&id<=1024&&CodeplugRecords.occupied(s,CodeplugRecords.Kind.CHANNEL,id);}
    // Scan-list format: 0=none, 1=selected channel, 2..1025=physical channels 1..1024.
    static int scanEncode(int id){return id==-1?1:id==0?0:id+1;}
    static int scanDecode(int wire){return wire==1?-1:wire>=2&&wire<=1025?wire-1:0;}
    static int scanRef(CodeplugSnapshot s,String value){
        int id=(int)CodeplugEditor.number(value,-1,1024,"Канал");
        if(id>0&&!channelExists(s,id))throw new IllegalArgumentException("Канал #"+id+" не существует");return scanEncode(id);
    }
    static void scan(CodeplugSnapshot s,int id,Map<String,String> fields){
        if(!CodeplugRecords.occupied(s,CodeplugRecords.Kind.SCAN,id))throw new IllegalArgumentException("Список сканирования не найден");
        byte[] b=s.scanLists;int o=0x40+(id-1)*88;
        for(Map.Entry<String,String> e:fields.entrySet()){
            String k=e.getKey(),v=e.getValue();switch(k){
                case "name":CodeplugEditor.text(b,o,15,v);break;
                case "members":
                    List<Integer> ids=members(v,32,1024,true);
                    for(int ref:ids)scanRef(s,""+ref);
                    Arrays.fill(b,o+16,o+80,(byte)0);for(int i=0;i<ids.size();i++)ByteUtil.putU16le(b,o+16+2*i,scanEncode(ids.get(i)));break;
                case "primary":case "secondary":case "revert":ByteUtil.putU16le(b,o+(k.equals("primary")?80:k.equals("secondary")?82:84),scanRef(s,v));break;
                case "hold":b[o+86]=(byte)units(v,25,6375,"Задержка сканирования, мс");break;
                case "sample":b[o+87]=(byte)units(v,250,63750,"Проверка приоритета, мс");break;
                default:throw new IllegalArgumentException("Неизвестное поле списка сканирования");
            }
        }
    }
    static int units(String value,int step,int max,String label){
        int n=(int)CodeplugEditor.number(value,0,max,label);if(n%step!=0)throw new IllegalArgumentException(label+": шаг "+step);return n/step;
    }
    static void dtmf(CodeplugSnapshot s,Map<String,String> fields){
        byte[] b=s.dtmfSettings;
        for(Map.Entry<String,String> e:fields.entrySet()){
            String k=e.getKey(),v=e.getValue();switch(k){
                case "self":case "kill":case "wake":case "up":case "down":
                    int off=k.equals("self")?0:k.equals("kill")?8:k.equals("wake")?24:k.equals("up")?48:80;
                    int len=k.equals("self")?8:k.equals("kill")||k.equals("wake")?16:30;
                    v=v.toUpperCase(Locale.ROOT);if(v.length()>len||!v.matches("[0-9ABCD*#←]*"))throw new IllegalArgumentException("DTMF: до "+len+" символов 0–9, A–D, *, #, ←");
                    Arrays.fill(b,off,off+len,(byte)255);for(int i=0;i<v.length();i++)b[off+i]=(byte)"0123456789ABCD*#←".indexOf(v.charAt(i));break;
                case "delimiter":case "groupCode":b[k.equals("delimiter")?40:41]=(byte)CodeplugEditor.number(v,0,15,"Символ DTMF");break;
                case "response":b[42]=(byte)CodeplugEditor.number(v,0,3,"Ответ декодера");break;
                case "reset":b[43]=(byte)CodeplugEditor.number(v,0,255,"Автосброс, с");break;
                case "killWake":b[44]=(byte)((b[44]&0x7f)|("1".equals(v)?0x80:0));break;
                case "killType":b[44]=(byte)((b[44]&0x9f)|((int)CodeplugEditor.number(v,0,3,"Тип блокировки")<<5));break;
                case "responseHold":case "decodeTime":
                    int tenths;try{tenths=new BigDecimal(v.replace(',','.')).movePointRight(1).intValueExact();}catch(RuntimeException ex){throw new IllegalArgumentException("Время: 0…25.5 с, шаг 0.1 с");}
                    if(tenths<0||tenths>255)throw new IllegalArgumentException("Время: 0…25.5 с");b[k.equals("responseHold")?112:113]=(byte)tenths;break;
                case "firstDelay":case "firstDuration":case "otherDuration":case "tail":
                    b[k.equals("firstDelay")?114:k.equals("firstDuration")?115:k.equals("otherDuration")?116:118]=(byte)units(v,100,25500,"Время DTMF, мс");break;
                case "rate":b[117]=(byte)CodeplugEditor.number(v,1,10,"Скорость DTMF, символов/с");break;
                default:throw new IllegalArgumentException("Неизвестное поле DTMF");
            }
        }
    }
    static void radio(CodeplugSnapshot s,Map<String,String> fields){
        for(Map.Entry<String,String> e:fields.entrySet()){
            if(!e.getKey().equals("vox"))throw new IllegalArgumentException("Неизвестная настройка рации");
            s.generalSettings[19]=(byte)CodeplugEditor.number(e.getValue(),1,10,"Чувствительность VOX");
        }
    }
    static void bands(CodeplugSnapshot s,Map<String,String> fields){
        for(Map.Entry<String,String> e:fields.entrySet()){
            int off=e.getKey().equals("uhfMin")?0:e.getKey().equals("uhfMax")?2:e.getKey().equals("vhfMin")?4:e.getKey().equals("vhfMax")?6:-1;
            if(off<0)throw new IllegalArgumentException("Неизвестная граница частот");
            ByteUtil.putU16le(s.deviceInfo,off,(int)CodeplugEditor.bcd(CodeplugEditor.number(e.getValue(),1,999,"Граница, целые МГц")));
        }
        for(int off:new int[]{0,4})if(OpenGd77CodeplugDecoder.bandLimitMhz(s.deviceInfo,off)>OpenGd77CodeplugDecoder.bandLimitMhz(s.deviceInfo,off+2))
            throw new IllegalArgumentException("Нижняя граница должна быть не выше верхней");
    }
    static void vfo(CodeplugSnapshot s,int slot,Map<String,String> fields){
        if(slot<0||slot>1)throw new IllegalArgumentException("VFO A/B");
        CodeplugSnapshot staged=CodeplugProject.copy(s);
        System.arraycopy(s.bootAndVfos,0x78+slot*56,staged.channelBank0,16,56);staged.channelBank0[0]|=1;
        CodeplugEditor.channel(staged,1,fields);
        System.arraycopy(staged.channelBank0,16,s.bootAndVfos,0x78+slot*56,56);
    }
}
