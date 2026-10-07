package ru.opengd77.satupdate;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** 128x64 monochrome OpenGD77 display buffer: x + (y/8)*128, bit y%8. */
final class BootImage {
    static final int WIDTH=128,HEIGHT=64,SIZE=1024;
    static byte[] pack(int[] pixels){
        if(pixels.length!=WIDTH*HEIGHT)throw new IllegalArgumentException("Размер заставки 128×64");
        byte[] out=new byte[SIZE];for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){
            int c=pixels[y*WIDTH+x],alpha=(c>>>24)&255;int r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
            int grey=(299*r+587*g+114*b)/1000;grey=(grey*alpha+255*(255-alpha))/255;
            if(grey<128)out[x+(y/8)*WIDTH]|=1<<(y%8);
        }return out;
    }
    static byte[] payload(byte[] image){try{AdditionalSettingsImage.Tlv tlv=new AdditionalSettingsImage(image).findTlv(1);if(tlv==null||tlv.payloadLength!=SIZE)return null;return Arrays.copyOfRange(image,tlv.payloadOffset,tlv.payloadOffset+SIZE);}catch(IllegalArgumentException e){return null;}}
    static byte[] replace(byte[] original,byte[] payload){
        if(payload.length!=SIZE)throw new IllegalArgumentException("Размер изображения заставки");
        byte[] image=original.clone();boolean erased=true;for(byte b:image)erased&=(b&255)==255;
        if(erased){System.arraycopy("OpenGD77".getBytes(StandardCharsets.US_ASCII),0,image,0,8);ByteUtil.putU32le(image,8,1);}
        new AdditionalSettingsImage(image);int off=12;boolean found=false;
        while(off+8<=image.length){
            long id=ByteUtil.u32le(image,off),length=ByteUtil.u32le(image,off+4);
            if(id==1){if(found||length!=SIZE)throw new IllegalArgumentException("Неподдерживаемый или повторный блок изображения");found=true;System.arraycopy(payload,0,image,off+8,SIZE);}
            if(id==0xffffffffL&&length==0xffffffffL){
                if(found)return image;
                if(off+8+SIZE+8>image.length)throw new IllegalArgumentException("Нет места для изображения");
                for(int i=off;i<image.length;i++)if((image[i]&255)!=255)throw new IllegalArgumentException("После конца TLV имеются данные");
                ByteUtil.putU32le(image,off,1);ByteUtil.putU32le(image,off+4,SIZE);System.arraycopy(payload,0,image,off+8,SIZE);return image;
            }
            if(id==0xffffffffL||length<=0||length>image.length-off-8)throw new IllegalArgumentException("Повреждённые дополнительные настройки");
            off+=8+(int)length;
        }
        if(found)return image;throw new IllegalArgumentException("Свободный конец TLV не найден");
    }
    static void permit(CodeplugProject project,byte[] mask){
        byte[] before=project.original.additionalSettings,after=project.working.additionalSettings;
        if(Arrays.equals(before,after))return;
        byte[] payload=payload(after);
        boolean valid=payload!=null&&Arrays.equals(replace(before,payload),after);
        if(payload==null){
            AdditionalSettingsImage.Tlv old=new AdditionalSettingsImage(before).findTlv(1);
            if(old!=null&&old.payloadLength==SIZE){
                byte[] removed=before.clone();boolean tail=true;for(int i=old.payloadOffset+SIZE;i<removed.length;i++)tail&=(removed[i]&255)==255;
                if(tail){Arrays.fill(removed,old.headerOffset,removed.length,(byte)255);valid=Arrays.equals(removed,after);
                    if(old.headerOffset==12){boolean erased=true;for(byte value:after)erased&=(value&255)==255;valid|=erased;}
                }
            }
        }
        if(!valid)throw new IllegalArgumentException("Посторонние изменения дополнительных настроек");
        for(int i=0;i<before.length;i++)if(before[i]!=after[i])mask[i]=(byte)255;
    }
}
