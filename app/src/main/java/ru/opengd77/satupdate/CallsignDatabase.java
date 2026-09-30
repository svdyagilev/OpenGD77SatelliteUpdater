package ru.opengd77.satupdate;

import java.io.*;
import java.text.Normalizer;
import java.util.*;

/** OpenGD77 IdN callsign database. This is not the codeplug DMR contact table. */
final class CallsignDatabase {
    static final int BASE=0x50000, SIZE0=0x40000, BASE1=0xd8000, SIZE1=0xd28000, HEADER=12;
    static final String LUT=" 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz.";
    static final int[] LENGTHS={16,20,24,32,40,48};
    static final class Options {
        final String region,separator; final boolean[] fields; final int chars;
        Options(String region,boolean[] fields,String separator,int chars) {
            this.region=region.trim();this.fields=fields.clone();this.separator=separator;this.chars=chars;
            if(!this.region.matches("([0-9]{1,8}([ ,;]+[0-9]{1,8})*)?"))throw new IllegalArgumentException("Регион: префиксы ID через пробел или запятую, например 401,250");
            if(fields.length!=5||(!separator.equals(" ")&&!separator.equals(".")))throw new IllegalArgumentException("Параметры колонок");
            boolean valid=false;for(int n:LENGTHS)if(n==chars)valid=true;
            if(!valid)throw new IllegalArgumentException("Длина записи");
        }
        boolean accepts(String id) {if(region.isEmpty())return true;for(String prefix:region.split("[ ,;]+"))if(id.startsWith(prefix))return true;return false;}
    }
    static final class Entry {final int id;final String text,encoded;Entry(int id,String text,String encoded){this.id=id;this.text=text;this.encoded=encoded;}}
    final List<Entry> entries;final byte[] first,second;final int chars,recordSize,sourceRows,duplicates,skipped;
    private CallsignDatabase(List<Entry> entries, int chars,int sourceRows,int duplicates,int skipped) {
        this.entries=Collections.unmodifiableList(entries);this.chars=chars;this.recordSize=3+chars*3/4;
        this.sourceRows=sourceRows;this.duplicates=duplicates;this.skipped=skipped;
        int n0=Math.min(entries.size(),(SIZE0-HEADER)/recordSize);
        first=new byte[HEADER+n0*recordSize];second=new byte[(entries.size()-n0)*recordSize];
        first[0]='I';first[1]='d';first[2]='N';first[3]=(byte)(0x4a+recordSize);
        first[4]='0';first[5]='0';first[6]='1';ByteUtil.putU32le(first,8,entries.size());
        for(int i=0;i<entries.size();i++) {
            Entry e=entries.get(i);byte[] target=i<n0?first:second;int off=i<n0?HEADER+i*recordSize:(i-n0)*recordSize;
            target[off]=(byte)e.id;target[off+1]=(byte)(e.id>>>8);target[off+2]=(byte)(e.id>>>16);
            byte[] packed=pack(e.encoded,chars);System.arraycopy(packed,0,target,off+3,packed.length);
        }
    }
    static CallsignDatabase fromRadio(byte[] first,byte[] second)throws IOException {
        if(first==null||first.length<HEADER)throw new IOException("Заголовок базы позывных не прочитан");
        if(first[0]!='I'||first[1]!='d'||first[2]!='N'||first[4]!='0'||first[5]!='0'||first[6]!='1'||first[7]!=0)
            throw new IOException("В FLASH не найден поддерживаемый заголовок IdN001");
        int recordSize=(first[3]&255)-0x4a;
        int chars=charsForRecordSize(recordSize);
        long count=ByteUtil.u32le(first,8);
        if(count>capacity(chars))throw new IOException("Размер базы повреждён: "+count+" записей");
        int inFirst=Math.min((int)count,(SIZE0-HEADER)/recordSize);
        int firstLength=HEADER+inFirst*recordSize;
        int secondLength=((int)count-inFirst)*recordSize;
        if(first.length!=firstLength||second==null||second.length!=secondLength)
            throw new IOException("Неполный или изменившийся образ базы позывных");
        List<Entry> entries=new ArrayList<>((int)count);int previous=-1;
        for(int i=0;i<(int)count;i++) {
            byte[] src=i<inFirst?first:second;int off=i<inFirst?HEADER+i*recordSize:(i-inFirst)*recordSize;
            int id=(src[off]&255)|((src[off+1]&255)<<8)|((src[off+2]&255)<<16);
            if(id<=0||id>=0xffffff)throw new IOException("Недопустимый DMR ID в записи "+(i+1));
            if(id<=previous)throw new IOException("ID в базе не отсортированы или повторяются около записи "+(i+1));
            previous=id;String text=unpack(src,off+3,chars);entries.add(new Entry(id,text,text));
        }
        return new CallsignDatabase(entries,chars,(int)count,0,0);
    }
    static int charsForRecordSize(int recordSize)throws IOException {
        for(int chars:LENGTHS)if(3+chars*3/4==recordSize)return chars;
        throw new IOException("Неизвестный размер записи базы: "+recordSize);
    }
    static Entry manualEntry(String idText,String text,int chars)throws IOException {
        final int id;
        try {
            String value=idText==null?"":idText.trim();
            if(!value.matches("[0-9]{1,8}"))throw new NumberFormatException();
            id=Integer.parseInt(value);
        } catch(NumberFormatException e) {throw new IOException("DMR ID должен содержать от 1 до 8 цифр");}
        if(id<=0||id>=0xffffff)throw new IOException("DMR ID вне допустимого диапазона");
        String clean=normalize(text==null?"":text);
        if(clean.isEmpty())throw new IOException("Текст записи пуст");
        if(clean.length()>512)throw new IOException("Текст записи слишком длинный");
        if(charsForRecordSize(3+chars*3/4)!=chars)throw new IOException("Неподдерживаемая длина записи");
        return new Entry(id,clean,clean.length()>chars?clean.substring(0,chars):clean);
    }
    CallsignDatabase withEntry(Integer oldId,Entry replacement)throws IOException {
        List<Entry> next=new ArrayList<>(entries.size()+1);boolean foundOld=false;
        for(Entry e:entries) {
            if(oldId!=null&&e.id==oldId){foundOld=true;continue;}
            if(e.id==replacement.id)throw new IOException("Такой DMR ID уже есть в базе");
            next.add(e);
        }
        if(oldId!=null&&!foundOld)throw new IOException("Редактируемая запись уже отсутствует");
        int pos=Collections.binarySearch(next,replacement,(a,b)->Integer.compare(a.id,b.id));
        next.add(pos<0?-pos-1:pos,replacement);
        if(next.size()>capacity(chars))throw new IOException("База превышает вместимость при длине "+chars);
        return new CallsignDatabase(next,chars,sourceRows,duplicates,skipped);
    }
    CallsignDatabase withoutEntry(int id)throws IOException {
        List<Entry> next=new ArrayList<>(entries.size());boolean found=false;
        for(Entry e:entries)if(e.id==id)found=true;else next.add(e);
        if(!found)throw new IOException("Запись уже отсутствует");
        return new CallsignDatabase(next,chars,sourceRows,duplicates,skipped);
    }
    static int capacity(int chars) {int record=3+chars*3/4;return (SIZE0-HEADER)/record+SIZE1/record;}
    static CallsignDatabase empty(int chars) {new Options("",new boolean[5]," ",chars);return new CallsignDatabase(new ArrayList<>(),chars,0,0,0);}
    static CallsignDatabase read(Reader input,Options options)throws IOException {
        Csv csv=new Csv(input);List<String> header=csv.row();if(header==null)throw new IOException("CSV пуст");
        Map<String,Integer> columns=new HashMap<>();for(int i=0;i<header.size();i++)columns.put(key(header.get(i)),i);
        int idCol=column(columns,"RADIOID","DMRID","ID","RADIOIDNUMBER"),callCol=column(columns,"CALLSIGN","CALL");
        if(idCol<0||callCol<0)throw new IOException("Нужна строка заголовков CSV: RADIO_ID (или DMRID/ID), CALLSIGN");
        int[] detail={column(columns,"FIRSTNAME","FNAME","NAME"),column(columns,"LASTNAME","SURNAME"),column(columns,"CITY"),column(columns,"STATE"),column(columns,"COUNTRY")};
        List<Entry> rows=new ArrayList<>();List<String> row;int total=0,skipped=0;
        while((row=csv.row())!=null) {
            if(row.size()==1&&row.get(0).trim().isEmpty())continue;
            if(++total>2000000)throw new IOException("CSV содержит более 2 млн строк");
            String idText=cell(row,idCol),call=cell(row,callCol);int id;
            try {if(!idText.matches("[0-9]{1,8}"))throw new NumberFormatException();id=Integer.parseInt(idText);if(id<=0||id>=0xffffff||call.isEmpty())throw new NumberFormatException();}
            catch(NumberFormatException e){skipped++;continue;}
            if(!options.accepts(Integer.toString(id)))continue;
            StringBuilder text=new StringBuilder(call);
            for(int i=0;i<5;i++)if(options.fields[i]){String value=cell(row,detail[i]);if(!value.isEmpty()&&!value.equalsIgnoreCase("None")&&!value.equalsIgnoreCase("null"))text.append(options.separator).append(value);}
            String display=normalize(text.toString());String encoded=display.length()>options.chars?display.substring(0,options.chars):display;
            rows.add(new Entry(id,display,encoded));
            if(rows.size()>1200000)throw new IOException("Слишком много записей: сузьте регион");
        }
        rows.sort((a,b)->Integer.compare(a.id,b.id));
        List<Entry> unique=new ArrayList<>(rows.size());int duplicates=0,previous=-1;
        for(Entry e:rows){if(e.id==previous){duplicates++;continue;}unique.add(e);previous=e.id;}
        if(unique.size()>capacity(options.chars))throw new IOException("Записей "+unique.size()+", вместимость "+capacity(options.chars)+". Сузьте регион или уменьшите длину.");
        if(unique.isEmpty())throw new IOException("После фильтра нет записей. Проверьте регион и CSV.");
        return new CallsignDatabase(unique,options.chars,total,duplicates,skipped);
    }
    private static String key(String s){return s.replace("\ufeff","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");}
    private static int column(Map<String,Integer> m,String... names){for(String n:names)if(m.containsKey(n))return m.get(n);return -1;}
    private static String cell(List<String> row,int i){return i<0||i>=row.size()?"":row.get(i).trim();}
    static String normalize(String s) {
        String ru="АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
        String[] latin={"A","B","V","G","D","E","Yo","Zh","Z","I","Y","K","L","M","N","O","P","R","S","T","U","F","Kh","Ts","Ch","Sh","Shch","","Y","","E","Yu","Ya"};
        StringBuilder translit=new StringBuilder();
        for(char c:s.toCharArray()){int i=ru.indexOf(Character.toUpperCase(c));if(i>=0)translit.append(Character.isLowerCase(c)?latin[i].toLowerCase(Locale.ROOT):latin[i]);else translit.append(c);}
        String clean=Normalizer.normalize(translit,Normalizer.Form.NFD).replaceAll("\\p{M}+","");StringBuilder out=new StringBuilder();
        for(char c:clean.toCharArray())out.append(Character.isWhitespace(c)?' ':LUT.indexOf(c)>=0?c:'.');
        return out.toString().replaceAll(" +"," ").trim();
    }
    static byte[] pack(String text,int chars) {
        byte[] b=new byte[chars*3/4];
        for(int i=0;i<chars;i++){int v=i<text.length()?LUT.indexOf(text.charAt(i)):0;if(v<0)v=63;
            int bit=i*6;for(int j=0;j<6;j++)if((v&(1<<(5-j)))!=0)b[(bit+j)/8]|=1<<(7-(bit+j)%8);}
        return b;
    }
    static String unpack(byte[] b,int off,int chars){StringBuilder s=new StringBuilder();for(int i=0;i<chars;i++){int v=0;for(int j=0;j<6;j++){int bit=i*6+j;v=(v<<1)|((b[off+bit/8]>>(7-bit%8))&1);}s.append(LUT.charAt(v));}return s.toString().trim();}

    /** Quoted comma/semicolon CSV, including BOM, escaped quotes and multiline fields. */
    private static final class Csv {
        final PushbackReader in;int separator=0;long count;
        Csv(Reader in){this.in=new PushbackReader(new BufferedReader(in),1);}
        int next()throws IOException{int c=in.read();if(++count>160L*1024*1024)throw new IOException("CSV слишком большой");return c;}
        List<String> row()throws IOException{
            List<String> cells=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false,closed=false,any=false;
            while(true){int c=next();if(c==0xfeff&&!any&&cells.isEmpty()&&field.length()==0)continue;
                if(c<0){if(quoted)throw new IOException("Незакрытая кавычка CSV");if(!any)return null;cells.add(field.toString());return cells;}
                any=true;
                if(quoted){if(c=='"'){int n=next();if(n=='"')field.append('"');else{quoted=false;closed=true;if(n>=0)in.unread(n);}}else field.append((char)c);}
                else if(c=='"'&&field.length()==0&&!closed)quoted=true;
                else if(c==separator||(separator==0&&(c==','||c==';'))){if(separator==0)separator=c;cells.add(field.toString());field.setLength(0);closed=false;}
                else if(c=='\n'||c=='\r'){if(c=='\r'){int n=next();if(n>=0&&n!='\n')in.unread(n);}cells.add(field.toString());return cells;}
                else if(closed){if(c!=' '&&c!='\t')throw new IOException("Лишние символы после кавычек CSV");}
                else field.append((char)c);
                if(field.length()>4096||cells.size()>64)throw new IOException("Недопустимый размер строки CSV");
            }
        }
    }
}
