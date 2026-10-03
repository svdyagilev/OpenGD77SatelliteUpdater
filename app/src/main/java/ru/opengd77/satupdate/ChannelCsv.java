package ru.opengd77.satupdate;

import java.io.*;
import java.util.*;

/** UTF-8 channel table, independent of the Windows CPS binary codeplug format. */
final class ChannelCsv {
    private static final String[] COLUMNS={"source_index","name","rx_mhz","tx_mhz","mode","power","tot_seconds","step_index","rx_only","beep","eco","vox","zone_skip","all_skip","fast","priority","location","latitude","longitude","rx_tone","tx_tone","wide","squelch","aprs_index","color_code","timeslot","contact_index","rx_group_index","dmr_id","dmo","roaming","ta_ts1","ta_ts2"};
    private static final String[] KEYS={"","name","rx","tx","mode","power","tot","step","rxOnly","beep","eco","vox","zoneSkip","allSkip","fast","priority","location","latitude","longitude","rxTone","txTone","wide","sql","aprs","cc","ts","contact","group","optionalId","dmo","roaming","ta1","ta2"};
    private ChannelCsv() {}
    private static String bool(boolean b){return b?"1":"0";}
    static String export(CodeplugModel model){
        StringBuilder out=new StringBuilder();row(out,Arrays.asList(COLUMNS));
        for(CodeplugModel.Channel c:model.channels){
            row(out,Arrays.asList(""+c.index,c.name,ChannelBatch.mhz(c.rxHz),ChannelBatch.mhz(c.txHz),c.digital?"DMR":"FM",
                ""+c.powerSetting,""+c.totSeconds,""+c.stepIndex,bool(c.rxOnly),bool(c.beepEnabled),bool(c.ecoEnabled),bool(c.vox),
                bool(c.zoneSkip),bool(c.allSkip),bool(c.fastCall),bool(c.priority),bool(c.useLocation),
                String.format(Locale.US,"%.4f",c.latitude),String.format(Locale.US,"%.4f",c.longitude),
                c.digital?"":c.rxTone.displayText(),c.digital?"":c.txTone.displayText(),c.digital?"":bool(c.wide25k),
                c.digital?"":""+(c.squelchOverride?c.squelchLevel:0),c.digital?"":""+c.aprsConfigIndex,
                c.digital?""+c.colorCode:"",c.digital?""+c.timeSlot:"",c.digital?""+c.contactIndex:"",c.digital?""+c.rxGroupIndex:"",
                c.digital?""+c.optionalDmrId:"",c.digital?bool(c.forceDmo):"",c.digital?bool(c.roaming):"",
                c.digital?""+c.taTxTs1:"",c.digital?""+c.taTxTs2:""));
        }return out.toString();
    }
    private static void row(StringBuilder out,List<String> cells){
        for(int i=0;i<cells.size();i++){if(i>0)out.append(';');out.append('"').append(cells.get(i).replace("\"","\"\"")).append('"');}out.append("\r\n");
    }
    static List<Map<String,String>> read(Reader reader)throws IOException {
        StringBuilder text=new StringBuilder();char[] block=new char[8192];int n;
        while((n=reader.read(block))!=-1){if(text.length()+n>4*1024*1024)throw new IOException("CSV больше 4 МБ");text.append(block,0,n);}
        if(text.length()>0&&text.charAt(0)=='\ufeff')text.deleteCharAt(0);
        char delimiter=delimiter(text.toString());List<List<String>> table=parse(text.toString(),delimiter);
        if(table.isEmpty())throw new IOException("Пустой CSV");List<String> header=table.get(0);Set<String> seen=new HashSet<>();List<String> keys=new ArrayList<>();
        for(String value:header){String column=value.trim().toLowerCase(Locale.ROOT);int index=Arrays.asList(COLUMNS).indexOf(column);
            if(index<0||!seen.add(column))throw new IOException("Неизвестный или повторный столбец: "+value);keys.add(KEYS[index]);}
        for(String required:new String[]{"name","rx_mhz","tx_mhz","mode"})if(!seen.contains(required))throw new IOException("Нужен столбец "+required);
        List<Map<String,String>> result=new ArrayList<>();
        for(int row=1;row<table.size();row++){
            List<String> cells=table.get(row);if(cells.size()==1&&cells.get(0).trim().isEmpty())continue;
            if(cells.size()!=header.size())throw new IOException("Строка "+(row+1)+": неверное число столбцов");
            Map<String,String> fields=new LinkedHashMap<>();
            for(int col=0;col<keys.size();col++){String value=cells.get(col).trim();if(!keys.get(col).isEmpty()&&!value.isEmpty())fields.put(keys.get(col),value);}
            String mode=fields.get("mode");if("FM".equalsIgnoreCase(mode))fields.put("mode","0");else if("DMR".equalsIgnoreCase(mode))fields.put("mode","1");
            else throw new IOException("Строка "+(row+1)+": режим FM или DMR");
            for(String key:new String[]{"name","rx","tx"})if(!fields.containsKey(key))throw new IOException("Строка "+(row+1)+": пустое поле "+key);
            result.add(fields);if(result.size()>1024)throw new IOException("В CSV не более 1024 каналов");
        }if(result.isEmpty())throw new IOException("В CSV нет каналов");return result;
    }
    static void append(CodeplugSnapshot snapshot,List<Map<String,String>> rows){
        int line=2;
        for(Map<String,String> fields:rows){
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Операция отменена");
            try{int id=CodeplugRecords.next(snapshot,CodeplugRecords.Kind.CHANNEL);CodeplugRecords.create(snapshot,CodeplugRecords.Kind.CHANNEL,id,fields);}
            catch(IllegalArgumentException e){throw new IllegalArgumentException("CSV, строка "+line+": "+e.getMessage(),e);}line++;
        }
    }
    private static char delimiter(String text){
        boolean quoted=false;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='"')quoted=!quoted;else if(!quoted&&(c==';'||c==','))return c;else if(!quoted&&(c=='\r'||c=='\n'))break;}return ';';
    }
    private static List<List<String>> parse(String text,char delimiter)throws IOException{
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false,closed=false,started=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(quoted){if(c=='"'){if(i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else{quoted=false;closed=true;}}else cell.append(c);continue;}
            if(c==delimiter){if(row.size()>=COLUMNS.length-1)throw new IOException("Слишком много столбцов CSV");row.add(cell.toString());cell.setLength(0);closed=false;started=false;}
            else if(c=='\r'||c=='\n'){if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;row.add(cell.toString());rows.add(row);if(rows.size()>2049)throw new IOException("Слишком много строк CSV");row=new ArrayList<>();cell.setLength(0);closed=false;started=false;}
            else if(c=='"'&&!started&&!closed){quoted=true;started=true;}
            else{if(closed||c=='"')throw new IOException("Некорректные кавычки в CSV");cell.append(c);started=true;}
        }
        if(quoted)throw new IOException("Незакрытые кавычки в CSV");
        if(cell.length()>0||!row.isEmpty()||closed){row.add(cell.toString());rows.add(row);}return rows;
    }
}
