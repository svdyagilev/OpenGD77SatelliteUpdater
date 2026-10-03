package ru.opengd77.satupdate;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;

final class CodeplugProjectStore {
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"codeplug-project.ogcproj"));}
    static File historyDirectory(Context c){return new File(c.getFilesDir(),"codeplug-history");}
    static File[] versions(Context c){return ProjectVersions.list(historyDirectory(c));}
    static synchronized void save(Context c,CodeplugProject p)throws IOException {
        byte[] data=p.encode();AtomicFile f=file(c);FileOutputStream out=null;
        CodeplugProject previous=null;
        try{previous=load(c);}catch(IOException e){android.util.Log.w("CodeplugProjectStore","Previous project is unreadable; preserving the valid new revision",e);}
        if(previous!=null)ProjectVersions.save(historyDirectory(c),previous.encode());
        ProjectVersions.save(historyDirectory(c),data);
        try{out=f.startWrite();out.write(data);f.finishWrite(out);}
        catch(IOException e){if(out!=null)f.failWrite(out);throw e;}
    }
    static CodeplugProject load(Context c)throws IOException{
        AtomicFile f=file(c);if(!f.getBaseFile().exists()&&!new File(f.getBaseFile()+".bak").exists())return null;
        try(InputStream in=f.openRead()){return CodeplugProject.read(in);}
    }
}
