package ru.opengd77.satupdate;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
final class SatelliteConfigStore {
    private static AtomicFile file(Context context){return new AtomicFile(new File(context.getFilesDir(),"satellite-configs.csv"));}
    static List<SatelliteConfig> load(Context context)throws IOException {
        AtomicFile file=file(context);try(InputStream input=file.getBaseFile().exists()?file.openRead():context.getAssets().open("Satellites.txt")){return SatelliteConfigParser.parse(input);}
    }
    static void validate(List<SatelliteConfig> configs){
        if(configs.size()>25)throw new IllegalArgumentException("Не более 25 спутников");Set<Integer> ids=new HashSet<>();Set<String> names=new HashSet<>();
        for(SatelliteConfig c:configs){
            if(c.catalogNumber<=0||!ids.add(c.catalogNumber))throw new IllegalArgumentException("NORAD должен быть положительным и уникальным");
            if(!c.name.matches("[A-Za-z0-9 ._+/-]{1,8}")||!names.add(c.name))throw new IllegalArgumentException("Уникальное имя спутника: 1–8 латинских символов");
            for(String value:new String[]{c.rx1,c.tx1,c.rx2,c.tx2,c.rx3,c.tx3}){double mhz=Double.parseDouble(value);if((Double.isNaN(mhz)||Double.isInfinite(mhz))||mhz<0||mhz>999.99999)throw new IllegalArgumentException("Проверьте частоты спутника");}
            for(String value:new String[]{c.ctcss,c.armCtcss}){double tone=Double.parseDouble(value);if((Double.isNaN(tone)||Double.isInfinite(tone))||tone<0||tone>254.1)throw new IllegalArgumentException("Субтон: 0…254.1 Гц");}
            if(!c.aprsConfig.matches("[A-Za-z0-9 *./-]{0,24}"))throw new IllegalArgumentException("Проверьте путь APRS (до 24 символов)");
        }
    }
    static void save(Context context,List<SatelliteConfig> configs)throws IOException {
        validate(configs);StringBuilder out=new StringBuilder("NORAD,Name,Rx1,Tx1,CTCSS,ArmCTCSS,Rx2,Tx2,Rx3,Tx3,APRS\n");
        for(SatelliteConfig c:configs)out.append(c.catalogNumber).append(',').append(c.name).append(',').append(c.rx1).append(',').append(c.tx1).append(',').append(c.ctcss).append(',').append(c.armCtcss).append(',').append(c.rx2).append(',').append(c.tx2).append(',').append(c.rx3).append(',').append(c.tx3).append(',').append(c.aprsConfig).append('\n');
        AtomicFile file=file(context);FileOutputStream stream=null;try{stream=file.startWrite();stream.write(out.toString().getBytes(StandardCharsets.US_ASCII));file.finishWrite(stream);}catch(IOException e){if(stream!=null)file.failWrite(stream);throw e;}
    }
}
