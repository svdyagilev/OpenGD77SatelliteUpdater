package ru.opengd77.satupdate;

import android.app.*;
import android.content.*;
import android.hardware.usb.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Standalone callsign database import/preview/write screen; never edits the codeplug project. */
public class CallsignActivity extends Activity {
    private static final String PERMISSION="ru.opengd77.satupdate.CALLSIGN_USB";
    private static final String SOURCE="https://database.radioid.net/static/user.csv";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<View> controls=new ArrayList<>();
    private EditText region,search;private Spinner length,separator,charset;private CheckBox[] fields;
    private TextView summary,status;private Button write,back;private ListView preview;
    private CallsignDatabase database,writeDatabase;private UsbManager manager;private UsbCdcSerialTransport transport;
    private OpenGd77Protocol protocol;private volatile boolean busy;private boolean permissionPending;
    private UsbDevice pendingDevice;private File exportFile;private String sourceLabel="";
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(!PERMISSION.equals(i.getAction())||!permissionPending)return;
        UsbDevice d=pendingDevice;pendingDevice=null;permissionPending=false;
        if(d!=null&&manager.hasPermission(d))perform(d);else finishTask("Доступ к USB не разрешён");
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);manager=(UsbManager)getSystemService(USB_SERVICE);transport=new UsbCdcSerialTransport(manager);protocol=new OpenGd77Protocol(transport);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);int pad=(int)(12*getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);
        ScrollView scroll=new ScrollView(this);LinearLayout form=new LinearLayout(this);form.setOrientation(1);scroll.addView(form);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        label(form,"База позывных • MD-9600",22);
        label(form,"Отдельная база DMR ID → позывной. Запись заменяет базу целиком.",14);
        label(form,"Регион — начало DMR ID; несколько через запятую. Пусто = все.",14);
        region=new EditText(this);region.setSingleLine(true);region.setText(getPreferences(0).getString("region","401"));form.addView(region);controls.add(region);
        button(form,"Загрузить RadioID.net",()->download());button(form,"Импортировать CSV",()->openCsv());
        label(form,"Кодировка CSV",14);charset=spinner(form,new String[]{"UTF-8","Windows-1251"});
        label(form,"Поля после позывного",14);String[] names={"Имя","Фамилия","Город","Область / штат","Страна"};fields=new CheckBox[5];
        for(int i=0;i<5;i++){fields[i]=new CheckBox(this);fields[i].setText(names[i]);fields[i].setChecked(getPreferences(0).getBoolean("field"+i,true));form.addView(fields[i]);controls.add(fields[i]);}
        label(form,"Разделитель",14);separator=spinner(form,new String[]{"Пробел","Точка"});separator.setSelection(getPreferences(0).getInt("separator",0));
        label(form,"Число символов вместе с позывным",14);length=spinner(form,new String[]{"16","20","24","32","40","48"});length.setSelection(Math.max(0,getPreferences(0).getInt("length",CallsignDatabase.LENGTHS.length-1)));
        button(form,"Применить фильтры / обновить предпросмотр",()->apply());
        label(form,"Предпросмотр показывает полные выбранные поля. В рации отображается начало строки до выбранного лимита символов. При 48 символах помещается больше имени и города, но меньше ID.",14);
        summary=label(form,"CSV ещё не загружен. Вместимость при 16 символах: "+CallsignDatabase.capacity(16),15);
        search=new EditText(this);search.setSingleLine(true);search.setHint("Поиск по ID или тексту записи");form.addView(search);controls.add(search);
        button(form,"Найти в подготовленной базе",()->showPreview());
        preview=new ListView(this);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(this);status.setTextIsSelectable(true);root.addView(status);
        write=button(root,"Записать базу в рацию",()->confirm(false));
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(1);root.addView(bottom);
        button(bottom,"Очистить базу рации",()->confirm(true));button(bottom,"Резервные копии",()->exportBackup());
        back=button(root,"Назад",()->leave());setContentView(root);
        TextWatcher changed=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){invalidatePreview();}public void afterTextChanged(Editable e){}};
        region.addTextChangedListener(changed);for(CheckBox f:fields)f.setOnCheckedChangeListener((v,c)->invalidatePreview());
        AdapterView.OnItemSelectedListener selected=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int n,long id){invalidatePreview();}public void onNothingSelected(AdapterView<?> p){}};
        length.setOnItemSelectedListener(selected);separator.setOnItemSelectedListener(selected);charset.setOnItemSelectedListener(selected);
        write.setEnabled(false);if(sourceFile().isFile())status.setText("Последний CSV сохранён. Нажмите «Применить фильтры».");
        IntentFilter f=new IntentFilter(PERMISSION);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);
    }
    private TextView label(LinearLayout parent,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);parent.addView(v);return v;}
    private Button button(LinearLayout parent,String text,Runnable action){Button b=new Button(this);b.setText(text);parent.addView(b);controls.add(b);b.setOnClickListener(v->{if(!busy)action.run();});return b;}
    private Spinner spinner(LinearLayout parent,String[] names){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));parent.addView(s);controls.add(s);return s;}
    private File sourceFile(){return new File(getFilesDir(),"callsign-source.csv");}
    private void invalidatePreview(){if(busy)return;database=null;write.setEnabled(false);preview.setAdapter(null);summary.setText("Нажмите «Применить фильтры». Вместимость: "+CallsignDatabase.capacity(CallsignDatabase.LENGTHS[length.getSelectedItemPosition()]));}
    private CallsignDatabase.Options options(){boolean[] f=new boolean[5];for(int i=0;i<5;i++)f[i]=fields[i].isChecked();return new CallsignDatabase.Options(region.getText().toString(),f,separator.getSelectedItemPosition()==0?" ":".",CallsignDatabase.LENGTHS[length.getSelectedItemPosition()]);}
    private void persistOptions(){android.content.SharedPreferences.Editor e=getPreferences(0).edit().putString("region",region.getText().toString()).putInt("length",length.getSelectedItemPosition()).putInt("separator",separator.getSelectedItemPosition());for(int i=0;i<5;i++)e.putBoolean("field"+i,fields[i].isChecked());e.apply();}
    private void setBusy(boolean value){busy=value;for(View v:controls)v.setEnabled(!value);write.setEnabled(!value&&database!=null);if(value)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void log(String text){runOnUiThread(()->status.setText(text));}
    private void finishTask(String text){runOnUiThread(()->{setBusy(false);status.setText(text);});}
    private Charset selectedCharset(){return Charset.forName(charset.getSelectedItemPosition()==0?"UTF-8":"windows-1251");}
    private CallsignDatabase parse(File file,CallsignDatabase.Options opts,Charset encoding)throws IOException {
        try(Reader r=new InputStreamReader(new FileInputStream(file),encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))){return CallsignDatabase.read(r,opts);}
    }
    private void load(File file,CallsignDatabase.Options opts,Charset encoding)throws IOException {
        loaded(parse(file,opts,encoding));
    }
    private void loaded(CallsignDatabase db){runOnUiThread(()->{database=db;setBusy(false);summary.setText("Записей: "+db.entries.size()+" / "+CallsignDatabase.capacity(db.chars)+"\nСтрок в CSV: "+db.sourceRows+"; некорректных: "+db.skipped+"; повторных ID: "+db.duplicates+"\nТекст: "+db.chars+" символов. "+sourceLabel);showPreview();});
    }
    private void apply(){try{CallsignDatabase.Options o=options();Charset c=selectedCharset();persistOptions();database=null;setBusy(true);worker.execute(()->{try{load(sourceFile(),o,c);}catch(Exception e){finishTask("Ошибка CSV: "+e.getMessage());}});}catch(Exception e){log(e.getMessage());}}
    private void openCsv(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(i,1);}
    private void download(){
        try{CallsignDatabase.Options o=options();persistOptions();charset.setSelection(0);database=null;setBusy(true);log("Загрузка RadioID.net...");
            worker.execute(()->{HttpURLConnection connection=null;File temp=new File(getCacheDir(),"callsign-download.csv");try{
                connection=(HttpURLConnection)new URL(SOURCE).openConnection();connection.setConnectTimeout(20000);connection.setReadTimeout(30000);connection.setRequestProperty("User-Agent","OpenGD77-CPS-Android/"+BuildConfig.VERSION_NAME);
                int code=connection.getResponseCode();if(code!=200)throw new IOException("RadioID HTTP "+code);
                if(!"https".equalsIgnoreCase(connection.getURL().getProtocol()))throw new IOException("Нужен HTTPS");
                try(InputStream in=connection.getInputStream()){copy(in,temp);}
                // Reject an HTML/error response before replacing a previously usable source.
                CallsignDatabase db=parse(temp,o,Charset.forName("UTF-8"));
                replaceSource(temp);sourceLabel="RadioID.net";runOnUiThread(()->{database=db;setBusy(false);summary.setText("RadioID.net: "+db.entries.size()+" записей / вместимость "+CallsignDatabase.capacity(db.chars)+"\nСтрок: "+db.sourceRows+"; пропущено: "+db.skipped+"; повторов: "+db.duplicates);showPreview();});
            }catch(Exception e){finishTask("Ошибка загрузки: "+e.getMessage());}finally{if(connection!=null)connection.disconnect();temp.delete();}});
        }catch(Exception e){log(e.getMessage());}
    }
    private void replaceSource(File temp)throws IOException { // same filesystem; rename is atomic
        if(!temp.renameTo(sourceFile()))throw new IOException("Не удалось сохранить CSV");
    }
    private void copy(InputStream in,File outFile)throws IOException {
        if(in==null)throw new IOException("Файл не открыт");try(FileOutputStream out=new FileOutputStream(outFile)){byte[] b=new byte[16384];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>80L*1024*1024)throw new IOException("CSV больше 80 МБ");out.write(b,0,n);}out.getFD().sync();}
    }
    private void showPreview(){if(database==null)return;String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);List<String> text=new ArrayList<>();int matches=0;for(CallsignDatabase.Entry e:database.entries){if(query.isEmpty()||Integer.toString(e.id).contains(query)||e.text.toLowerCase(Locale.ROOT).contains(query)){matches++;if(text.size()<200)text.add(e.id+"   "+e.text+(e.encoded.length()<e.text.length()?"  →  "+e.encoded+"…":""));}}preview.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,text));status.setText("Найдено: "+matches+". Показано: "+text.size()+". При записи используются все "+database.entries.size()+" записей подготовленной базы.");}
    private void confirm(boolean clear){
        if(!clear&&database==null)return;
        writeDatabase=clear?CallsignDatabase.empty(CallsignDatabase.LENGTHS[length.getSelectedItemPosition()]):database;
        String text=clear?"Удалить все записи базы позывных из рации?":"Заменить базу позывных в рации?\nЗаписей: "+writeDatabase.entries.size()+"\nСимволов: "+writeDatabase.chars;
        new AlertDialog.Builder(this).setTitle(clear?"Очистить базу позывных?":"Записать базу позывных?").setMessage(text+"\n\nПеред записью будет сохранена резервная копия, каждый записанный сектор будет проверен чтением. Не отключайте USB и питание до завершения.")
            .setNegativeButton("Отмена",null).setPositiveButton("Записать",(d,w)->begin()).show();
    }
    private void begin(){setBusy(true);UsbDevice d=transport.findDevice();if(d==null){finishTask("MD-9600 не найдена по USB");return;}if(manager.hasPermission(d)){perform(d);return;}
        pendingDevice=d;permissionPending=true;Intent i=new Intent(PERMISSION).setPackage(getPackageName());PendingIntent pi=PendingIntent.getBroadcast(this,7,i,Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
        try{manager.requestPermission(d,pi);}catch(Exception e){permissionPending=false;pendingDevice=null;finishTask(e.getMessage());}}
    @Override protected void onResume(){super.onResume();if(permissionPending&&pendingDevice!=null&&manager.hasPermission(pendingDevice)){UsbDevice d=pendingDevice;permissionPending=false;pendingDevice=null;perform(d);}}
    private void perform(UsbDevice device){worker.execute(()->{
        boolean entered=false;final boolean[] started={false};String result;
        try{
            transport.open(device);OpenGd77Protocol.FirmwareInfo info=protocol.readFirmwareInfo();CallsignWritePlan.checkRadio(info.radioType,info.structVersion,info.flashId);
            entered=true;protocol.enterProgrammingMode(true,"Writing DMR IDs");
            CallsignWritePlan.Memory memory=new CallsignWritePlan.Memory(){public byte[] read(int a,int n)throws IOException{return protocol.readFlash(a,n);}public void write(int a,byte[] b)throws IOException{started[0]=true;protocol.writeFlashSector(a,b);}};
            List<CallsignWritePlan.Sector> plan=CallsignWritePlan.prepare(writeDatabase,memory,this::log);
            CallsignWritePlan.execute(plan,memory,s->saveBackup(s,info),this::log);
            protocol.closeProgrammingMode();entered=false;
            result="База обработана. Проверено секторов: "+plan.size()+".";
            try{protocol.reboot();result+=" Рация перезагружается.";}catch(IOException e){result+=" Перезапустите рацию вручную.";}
        }catch(Exception e){result="Ошибка: "+e.getMessage()+(started[0]?"\nЗапись могла выполниться частично. Резервная копия сохранена. Перезапустите рацию и повторно запишите подготовленную базу.":"\nЗапись базы не выполнялась.");}
        finally{if(entered)try{protocol.closeProgrammingMode();}catch(Exception ignored){}transport.close();}
        finishTask(result);
    });}
    private void saveBackup(List<CallsignWritePlan.Sector> sectors,OpenGd77Protocol.FirmwareInfo info)throws IOException {
        File dir=new File(getFilesDir(),"callsign-backups");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Не создать папку backup");
        File file=new File(dir,"DMRID-"+System.currentTimeMillis()+".zip");
        try(FileOutputStream out=new FileOutputStream(file);ZipOutputStream zip=new ZipOutputStream(out)){
            entry(zip,"README.txt",("MD-9600 callsign replacement backup\nFirmware: "+info.fwRevision+"\nRadio info: "+info.structVersion+"\nFlash ID: "+Long.toHexString(info.flashId)+"\nTarget count: "+writeDatabase.entries.size()+"\nText chars: "+writeDatabase.chars+"\nFLASH-before contains original complete sectors at physical addresses.\nOnly touched sectors are backed up; all other memory is preserved.\nNot a firmware image or codeplug file.\n").getBytes("UTF-8"));
            for(CallsignWritePlan.Sector s:sectors){String name=String.format(Locale.ROOT,"%08x.bin",s.address);entry(zip,"FLASH-before/"+name,s.before);entry(zip,"FLASH-after/"+name,s.after);}
            zip.finish();zip.flush();out.getFD().sync();
        }catch(IOException e){file.delete();throw e;}
    }
    private static void entry(ZipOutputStream out,String name,byte[] bytes)throws IOException{out.putNextEntry(new ZipEntry(name));out.write(bytes);out.closeEntry();}
    private void exportBackup(){File[] files=new File(getFilesDir(),"callsign-backups").listFiles((d,n)->n.endsWith(".zip"));if(files==null||files.length==0){log("Резервных копий пока нет");return;}Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));String[] names=new String[files.length];for(int i=0;i<files.length;i++)names[i]=files[i].getName();new AlertDialog.Builder(this).setTitle("Сохранить резервную копию").setItems(names,(d,n)->{exportFile=files[n];Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,exportFile.getName());startActivityForResult(intent,2);}).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==1){try{CallsignDatabase.Options o=options();Charset c=selectedCharset();persistOptions();database=null;setBusy(true);worker.execute(()->{File tmp=new File(getCacheDir(),"callsign-import.csv");try(InputStream in=getContentResolver().openInputStream(data.getData())){copy(in,tmp);CallsignDatabase db=parse(tmp,o,c);replaceSource(tmp);sourceLabel="Импорт CSV";loaded(db);}catch(Exception e){finishTask("Ошибка импорта: "+e.getMessage());}finally{tmp.delete();}});}catch(Exception e){log(e.getMessage());}}
        else if(request==2&&exportFile!=null){File file=exportFile;setBusy(true);worker.execute(()->{try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException("Файл не открыт");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();finishTask("Резервная копия сохранена.");}catch(Exception e){finishTask("Ошибка экспорта: "+e.getMessage());}});}
    }
    private void leave(){if(!busy)finish();}
    @Override public void onBackPressed(){leave();}
    @Override protected void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception ignored){}if(!busy){transport.close();worker.shutdown();}super.onDestroy();}
}
