package ru.opengd77.satupdate;

import java.util.*;

final class CodeplugIntegrity {
    static void channelMask(byte[] m,int o){
        Arrays.fill(m,o,o+0x24,(byte)255);m[o+0x24]=(byte)255;m[o+0x25]=(byte)0xc0;m[o+0x26]=(byte)0xed;
        Arrays.fill(m,o+0x27,o+0x2a,(byte)255);Arrays.fill(m,o+0x2b,o+0x30,(byte)255);
        m[o+0x30]=15;m[o+0x31]=0x40;m[o+0x33]=0x77;m[o+0x36]=(byte)0xf0;m[o+0x37]=(byte)255;
    }
    static byte[][] masks(CodeplugProject p){
        byte[][] a=CodeplugProject.blocks(p.original),b=CodeplugProject.blocks(p.working);
        byte[][] mask=CodeplugProject.blocks(CodeplugProject.copy(p.original));for(byte[] m:mask)Arrays.fill(m,(byte)0);
        Arrays.fill(mask[0],0,8,(byte)255);Arrays.fill(mask[1],0,12,(byte)255);mask[1][19]=(byte)255;
        Arrays.fill(mask[2],0,44,(byte)255);mask[2][44]=(byte)0xe0;
        Arrays.fill(mask[2],48,78,(byte)255);Arrays.fill(mask[2],80,110,(byte)255);Arrays.fill(mask[2],112,119,(byte)255);
        mask[7][0]=(byte)255;Arrays.fill(mask[7],0x28,0x48,(byte)255);
        channelMask(mask[7],0x78);channelMask(mask[7],0xb0);
        BootImage.permit(p,mask[12]);
        CodeplugModel model=p.model();
        for(CodeplugRecords.Kind k:CodeplugRecords.Kind.values())for(int id=1;id<=CodeplugRecords.limit(k);id++){
            int block=CodeplugRecords.block(k,id),o=CodeplugRecords.offset(k,id),size=CodeplugRecords.size(k);
            boolean before=CodeplugRecords.occupied(p.original,k,id),after=CodeplugRecords.occupied(p.working,k,id);
            if(!before&&!after)continue;
            if(before&&!after){
                CodeplugSnapshot expected=CodeplugProject.copy(p.original);CodeplugRecords.tombstone(expected,p.original,k,id);
                byte[] tomb=CodeplugProject.blocks(expected)[block];
                for(int j=o;j<o+size;j++)if(b[block][j]!=tomb[j])throw new IllegalArgumentException("Удалённая запись содержит посторонние изменения");
                if(k==CodeplugRecords.Kind.CHANNEL||k==CodeplugRecords.Kind.ZONE)mask[block][CodeplugRecords.marker(k,id)]|=1<<((id-1)%8);
                else if(k==CodeplugRecords.Kind.GROUP||k==CodeplugRecords.Kind.SCAN)mask[block][id-1]=(byte)255;
                else mask[block][o]=(byte)255;
                continue;
            }
            if(!before){
                CodeplugRecords.validateNew(p.working,model,k,id);Arrays.fill(mask[block],o,o+size,(byte)255);
                if(k==CodeplugRecords.Kind.CHANNEL||k==CodeplugRecords.Kind.ZONE)mask[block][CodeplugRecords.marker(k,id)]|=1<<((id-1)%8);
                else if(k==CodeplugRecords.Kind.GROUP||k==CodeplugRecords.Kind.SCAN)mask[block][id-1]=(byte)255;
                continue;
            }
            byte[] m=mask[block];
            switch(k){
                case CHANNEL:channelMask(m,o);break;
                case DMR:Arrays.fill(m,o,o+21,(byte)255);m[o+23]=3;break;
                case DTMF:case ZONE:Arrays.fill(m,o,o+size,(byte)255);break;
                case GROUP:Arrays.fill(m,o,o+80,(byte)255);m[id-1]=(byte)255;break;
                case SCAN:Arrays.fill(m,o,o+15,(byte)255);Arrays.fill(m,o+16,o+88,(byte)255);break;
                case APRS:Arrays.fill(m,o,o+59,(byte)255);m[o+61]=7;break;
            }
            boolean outside=false;for(int j=o;j<o+size;j++)outside|=((a[block][j]^b[block][j])&~m[j]&255)!=0;
            // A deleted slot may have been reused before writing. Only a fully valid fresh record may replace it.
            if(outside){CodeplugRecords.validateNew(p.working,model,k,id);Arrays.fill(m,o,o+size,(byte)255);}
        }
        for(int off=0x0c;off<0x20;off+=2){
            int old=ByteUtil.u16le(a[7],off),now=ByteUtil.u16le(b[7],off);
            if(old>=1&&old<=1024&&now==0x8000&&!CodeplugRecords.occupied(p.working,CodeplugRecords.Kind.DMR,old)){
                mask[7][off]=(byte)255;mask[7][off+1]=(byte)255;
            }
        }
        for(int block=0;block<a.length;block++)for(int i=0;i<a[block].length;i++)
            if(((a[block][i]^b[block][i])&~mask[block][i]&255)!=0)throw new IllegalArgumentException("Изменения вне поддерживаемых полей, блок "+block);
        validateChangedLists(p);
        return mask;
    }
    private static void validateChangedLists(CodeplugProject p){
        byte[][] before=CodeplugProject.blocks(p.original),after=CodeplugProject.blocks(p.working);
        for(int id=1;id<=76;id++)if(CodeplugRecords.occupied(p.working,CodeplugRecords.Kind.GROUP,id)){
            int o=CodeplugRecords.offset(CodeplugRecords.Kind.GROUP,id);
            if(before[11][id-1]==after[11][id-1]&&Arrays.equals(Arrays.copyOfRange(before[11],o+16,o+80),Arrays.copyOfRange(after[11],o+16,o+80)))continue;
            int count=(after[11][id-1]&255)-1;if(count<0||count>32)throw new IllegalArgumentException("Некорректная длина группы #"+id);
            Set<Integer> seen=new HashSet<>();for(int i=0;i<32;i++){
                int ref=ByteUtil.u16le(after[11],o+16+i*2);
                if(i>=count){if(ref!=0)throw new IllegalArgumentException("Лишние контакты группы");}
                else if(ref<1||ref>1024||!seen.add(ref))throw new IllegalArgumentException("Некорректные контакты группы");
            }
        }
    }
    static final class Edge {
        final String owner;final CodeplugRecords.Kind kind;final int id;
        Edge(String owner,CodeplugRecords.Kind kind,int id){this.owner=owner;this.kind=kind;this.id=id;}
        String key(){return owner+"/"+kind+"/"+id;}
    }
    private static void add(List<Edge> out,String owner,CodeplugRecords.Kind kind,int id){if(id>0)out.add(new Edge(owner,kind,id));}
    private static void channelEdges(List<Edge> out,String owner,byte[] b,int o){
        add(out,owner,CodeplugRecords.Kind.DMR,ByteUtil.u16le(b,o+46));
        add(out,owner,CodeplugRecords.Kind.GROUP,b[o+43]&255);add(out,owner,CodeplugRecords.Kind.APRS,b[o+45]&255);
    }
    static List<Edge> edges(CodeplugSnapshot s){
        List<Edge> out=new ArrayList<>();byte[][] blocks=CodeplugProject.blocks(s);
        for(int id=1;id<=1024;id++)if(CodeplugRecords.occupied(s,CodeplugRecords.Kind.CHANNEL,id))channelEdges(out,"Канал #"+id,blocks[CodeplugRecords.block(CodeplugRecords.Kind.CHANNEL,id)],CodeplugRecords.offset(CodeplugRecords.Kind.CHANNEL,id));
        for(int v=0;v<2;v++)channelEdges(out,"VFO "+(v==0?"A":"B"),s.bootAndVfos,0x78+v*56);
        for(int off=12;off<32;off+=2){int id=ByteUtil.u16le(s.bootAndVfos,off);if(id<=1024)add(out,"Быстрая команда #"+((off-12)/2),CodeplugRecords.Kind.DMR,id);}
        for(CodeplugRecords.Kind k:new CodeplugRecords.Kind[]{CodeplugRecords.Kind.ZONE,CodeplugRecords.Kind.SCAN,CodeplugRecords.Kind.GROUP})
            for(int id=1;id<=CodeplugRecords.limit(k);id++)if(CodeplugRecords.occupied(s,k,id)){
                int o=CodeplugRecords.offset(k,id),count=k==CodeplugRecords.Kind.ZONE?80:k==CodeplugRecords.Kind.GROUP?Math.min(32,Math.max(0,(s.rxGroups[id-1]&255)-1)):35;
                byte[] b=blocks[CodeplugRecords.block(k,id)];String owner=(k==CodeplugRecords.Kind.ZONE?"Зона":k==CodeplugRecords.Kind.SCAN?"Сканирование":"Группа")+" #"+id;
                for(int n=0;n<count;n++){int ref=ByteUtil.u16le(b,o+16+2*n);if(k==CodeplugRecords.Kind.SCAN)ref=ref>1?ref-1:0;
                    add(out,owner,k==CodeplugRecords.Kind.GROUP?CodeplugRecords.Kind.DMR:CodeplugRecords.Kind.CHANNEL,ref);}
            }
        return out;
    }
    private static boolean exists(CodeplugSnapshot s,Edge e){return e.id<=CodeplugRecords.limit(e.kind)&&CodeplugRecords.occupied(s,e.kind,e.id);}
    static void links(CodeplugSnapshot original,CodeplugSnapshot effective){
        Set<String> previous=new HashSet<>();for(Edge e:edges(original))previous.add(e.key());
        for(Edge e:edges(effective))if(!exists(effective,e)&&(!previous.contains(e.key())||exists(original,e)))
            throw new IllegalArgumentException(e.owner+": ссылка на отсутствующую запись "+e.kind+" #"+e.id+". Выберите также связанные разделы для записи.");
    }
}
