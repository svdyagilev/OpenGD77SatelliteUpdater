package ru.opengd77.satupdate;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Validated OpenGD77 TLVs. Unknown payloads always stay with the receiving radio. */
final class AdditionalData {
    static LinkedHashMap<Long,byte[]> entries(byte[] data){
        LinkedHashMap<Long,byte[]> out=new LinkedHashMap<>();boolean erased=true;for(byte b:data)erased&=(b&255)==255;if(erased)return out;
        new AdditionalSettingsImage(data);int off=12;
        while(off+8<=data.length){long id=ByteUtil.u32le(data,off),n=ByteUtil.u32le(data,off+4);
            if(id==0xffffffffL&&n==0xffffffffL){for(int i=off;i<data.length;i++)if((data[i]&255)!=255)throw new IllegalArgumentException("Данные после конца TLV");return out;}
            if(id==0xffffffffL||n<=0||n>data.length-off-8||out.containsKey(id))throw new IllegalArgumentException("Повреждённый или повторный блок TLV");
            if(id==1&&n!=1024||id==2&&n!=512||id==3&&n!=OpenGd77SatelliteEncoder.SATELLITE_PAYLOAD_SIZE)throw new IllegalArgumentException("Неподдерживаемый размер TLV #"+id);
            out.put(id,Arrays.copyOfRange(data,off+8,off+8+(int)n));off+=8+(int)n;
        }throw new IllegalArgumentException("Отсутствует конец TLV");
    }
    static byte[] mergeKnown(byte[] current,byte[] incoming){
        LinkedHashMap<Long,byte[]> a=entries(current),b=entries(incoming);
        for(long id:new long[]{1,2,3}){if(b.containsKey(id))a.put(id,b.get(id));else a.remove(id);}
        byte[] out=new byte[current.length];Arrays.fill(out,(byte)255);if(a.isEmpty())return out;
        System.arraycopy("OpenGD77".getBytes(StandardCharsets.US_ASCII),0,out,0,8);ByteUtil.putU32le(out,8,1);int off=12;
        for(Map.Entry<Long,byte[]> e:a.entrySet()){if(off+8+e.getValue().length+8>out.length)throw new IllegalArgumentException("Недостаточно места для дополнительных данных");ByteUtil.putU32le(out,off,e.getKey());ByteUtil.putU32le(out,off+4,e.getValue().length);System.arraycopy(e.getValue(),0,out,off+8,e.getValue().length);off+=8+e.getValue().length;}return out;
    }
    static void permit(CodeplugProject p,byte[] mask){
        if(Arrays.equals(p.original.additionalSettings,p.working.additionalSettings))return;
        LinkedHashMap<Long,byte[]> a=entries(p.original.additionalSettings),b=entries(p.working.additionalSettings);
        Set<Long> ids=new HashSet<>(a.keySet());ids.addAll(b.keySet());for(long id:ids)if(id!=1&&id!=2&&id!=3&&!Arrays.equals(a.get(id),b.get(id)))throw new IllegalArgumentException("Изменение неизвестного блока TLV #"+id);
        for(int i=0;i<mask.length;i++)if(p.original.additionalSettings[i]!=p.working.additionalSettings[i])mask[i]=(byte)255;
    }
}
