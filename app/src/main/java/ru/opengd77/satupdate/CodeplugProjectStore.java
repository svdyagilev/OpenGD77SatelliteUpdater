package ru.opengd77.satupdate;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;

final class CodeplugProjectStore {
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"codeplug-project.ogcproj"));}
    static void save(Context c,CodeplugProject p)throws IOException {
        byte[] data=p.encode();AtomicFile f=file(c);FileOutputStream out=null;
        try{out=f.startWrite();out.write(data);f.finishWrite(out);}
        catch(IOException e){if(out!=null)f.failWrite(out);throw e;}
    }
    static CodeplugProject load(Context c)throws IOException{
        AtomicFile f=file(c);if(!f.getBaseFile().exists()&&!new File(f.getBaseFile()+".bak").exists())return null;
        try(InputStream in=f.openRead()){return CodeplugProject.read(in);}
    }
}
