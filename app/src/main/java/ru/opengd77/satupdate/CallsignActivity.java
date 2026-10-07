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
public class CallsignActivity extends ScreenActivity {
    private static final String PERMISSION="ru.opengd77.satupdate.CALLSIGN_USB";
    private static final String SOURCE="https://database.radioid.net/static/user.csv";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<View> controls=new ArrayList<>();
    private final List<View> csvOnlyControls=new ArrayList<>();
    private final List<CallsignDatabase.Entry> visibleEntries=new ArrayList<>();
    private EditText region,search;private Spinner length,separator,charset;private CheckBox[] fields;
    private TextView summary,status;private Button write,addEntryButton,back,openEditorButton;private ListView preview;
    private LinearLayout formPage,editorPage;
    private TextView previewSummary;
    private boolean editorOpen;
    private CallsignDatabase database,writeDatabase;private UsbManager manager;private UsbCdcSerialTransport transport;
    private OpenGd77Protocol protocol;private volatile boolean busy;private boolean permissionPending,radioSource,pendingWrite;
    private UsbDevice pendingDevice;private File exportFile;private String sourceLabel="";
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(!PERMISSION.equals(i.getAction())||!permissionPending)return;
        UsbDevice d=pendingDevice;pendingDevice=null;permissionPending=false;
        if(d!=null&&manager.hasPermission(d))perform(d);else finishTask("Доступ к USB не разрешён");
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);manager=(UsbManager)getSystemService(USB_SERVICE);transport=new UsbCdcSerialTransport(manager);protocol=new OpenGd77Protocol(transport);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);int pad=(int)(12*getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);formPage=root;
        ScrollView scroll=new ScrollView(this);LinearLayout form=new LinearLayout(this);form.setOrientation(1);scroll.addView(form);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        label(form,"База позывных • MD-9600",22);
        label(form,"Отдельная база DMR ID → позывной. Запись заменяет базу целиком.",14);
        label(form,"Регион — начало DMR ID; несколько через запятую. Пусто = все.",14);
        region=new EditText(this);region.setSingleLine(true);region.setText(getPreferences(0).getString("region","401"));form.addView(region);controls.add(region);csvOnlyControls.add(region);
        button(form,"Прочитать базу из рации",()->readFromRadio());
        button(form,"Загрузить RadioID.net",()->download());button(form,"Импортировать CSV",()->openCsv());
        label(form,"Кодировка CSV",14);charset=spinner(form,new String[]{"UTF-8","Windows-1251"});csvOnlyControls.add(charset);
        label(form,"Поля после позывного",14);String[] names={"Имя","Фамилия","Город","Область / штат","Страна"};fields=new CheckBox[5];
        for(int i=0;i<5;i++){fields[i]=new CheckBox(this);fields[i].setText(names[i]);fields[i].setChecked(getPreferences(0).getBoolean("field"+i,true));form.addView(fields[i]);controls.add(fields[i]);csvOnlyControls.add(fields[i]);}
        label(form,"Разделитель",14);separator=spinner(form,new String[]{"Пробел","Точка"});separator.setSelection(getPreferences(0).getInt("separator",0));csvOnlyControls.add(separator);
        label(form,"Число символов вместе с позывным",14);length=spinner(form,new String[]{"16","20","24","32","40","48"});length.setSelection(Math.max(0,getPreferences(0).getInt("length",CallsignDatabase.LENGTHS.length-1)));csvOnlyControls.add(length);
        Button apply=button(form,"Применить фильтры / обновить предпросмотр",()->apply());csvOnlyControls.add(apply);
        label(form,"Предпросмотр показывает полные выбранные поля. В рации отображается начало строки до выбранного лимита символов. При 48 символах помещается больше имени и города, но меньше ID.",14);
        summary=label(form,"CSV ещё не загружен. Вместимость при 16 символах: "+CallsignDatabase.capacity(16),15);
        openEditorButton=button(form,"Список и редактор позывных  ›",()->openEditor());
        editorPage=new LinearLayout(this);editorPage.setOrientation(1);editorPage.setPadding(pad,pad,pad,pad);
        button(editorPage,"‹  Назад к базе позывных",()->closeEditor());
        label(editorPage,"Список позывных",22);
        search=new EditText(this);search.setSingleLine(true);search.setHint("Поиск по ID или тексту записи");editorPage.addView(search);controls.add(search);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v,action,event)->{
            if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){showPreview();hideEditorKeyboard();return true;}return false;
        });
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(0);editorPage.addView(actions);
        Button find=button(actions,"Найти",()->{showPreview();hideEditorKeyboard();});
        find.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        addEntryButton=button(actions,"Добавить",()->editEntry(null));
        addEntryButton.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        previewSummary=label(editorPage,"",14);
        preview=new ListView(this);editorPage.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        preview.setOnItemClickListener((parent,view,position,id)->{if(!busy&&position<visibleEntries.size())editEntry(visibleEntries.get(position));});
        label(editorPage,"Правки сохраняются в подготовленной базе. Для передачи в рацию вернитесь и нажмите «Записать базу в рацию».",14);
        status=new TextView(this);status.setTextIsSelectable(true);root.addView(status);
        write=button(root,"Записать базу в рацию",()->confirm(false));
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(1);root.addView(bottom);
        button(bottom,"Очистить базу рации",()->confirm(true));button(bottom,"Резервные копии",()->exportBackup());
        back=button(root,"Назад",()->leave());
        FrameLayout pages=new FrameLayout(this);pages.addView(formPage);pages.addView(editorPage);editorPage.setVisibility(View.GONE);setContentView(pages);
        TextWatcher changed=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(!radioSource)invalidatePreview();}public void afterTextChanged(Editable e){}};
        region.addTextChangedListener(changed);for(CheckBox f:fields)f.setOnCheckedChangeListener((v,c)->{if(!radioSource)invalidatePreview();});
        AdapterView.OnItemSelectedListener selected=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int n,long id){if(!radioSource)invalidatePreview();}public void onNothingSelected(AdapterView<?> p){}};
        length.setOnItemSelectedListener(selected);separator.setOnItemSelectedListener(selected);charset.setOnItemSelectedListener(selected);
        write.setEnabled(false);addEntryButton.setEnabled(false);openEditorButton.setEnabled(false);if(sourceFile().isFile())status.setText("Последний CSV сохранён. Нажмите «Применить фильтры».");
        IntentFilter f=new IntentFilter(PERMISSION);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);
    }
    private TextView label(LinearLayout parent,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);parent.addView(v);return v;}
    private Button button(LinearLayout parent,String text,Runnable action){Button b=new Button(this);b.setText(text);parent.addView(b);controls.add(b);b.setOnClickListener(v->{if(!busy)action.run();});return b;}
    private Spinner spinner(LinearLayout parent,String[] names){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));parent.addView(s);controls.add(s);return s;}
    private File sourceFile(){return new File(getFilesDir(),"callsign-source.csv");}
    private void invalidatePreview(){if(busy)return;database=null;write.setEnabled(false);addEntryButton.setEnabled(false);openEditorButton.setEnabled(false);visibleEntries.clear();preview.setAdapter(null);previewSummary.setText("");summary.setText("Нажмите «Применить фильтры». Вместимость: "+CallsignDatabase.capacity(CallsignDatabase.LENGTHS[length.getSelectedItemPosition()]));}
    private CallsignDatabase.Options options(){boolean[] f=new boolean[5];for(int i=0;i<5;i++)f[i]=fields[i].isChecked();return new CallsignDatabase.Options(region.getText().toString(),f,separator.getSelectedItemPosition()==0?" ":".",CallsignDatabase.LENGTHS[length.getSelectedItemPosition()]);}
    private void persistOptions(){android.content.SharedPreferences.Editor e=getPreferences(0).edit().putString("region",region.getText().toString()).putInt("length",length.getSelectedItemPosition()).putInt("separator",separator.getSelectedItemPosition());for(int i=0;i<5;i++)e.putBoolean("field"+i,fields[i].isChecked());e.apply();}
    private void setBusy(boolean value){busy=value;for(View v:controls)v.setEnabled(!value);if(!value&&radioSource)for(View v:csvOnlyControls)v.setEnabled(false);write.setEnabled(!value&&database!=null&&!database.entries.isEmpty());addEntryButton.setEnabled(!value&&database!=null);openEditorButton.setEnabled(!value&&database!=null);if(value)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void setRadioSource(boolean value){radioSource=value;for(View v:csvOnlyControls)v.setEnabled(!busy&&!value);}
    private void log(String text){runOnUiThread(()->status.setText(text));}
    private void finishTask(String text){runOnUiThread(()->{setBusy(false);status.setText(text);});}
    private Charset selectedCharset(){return Charset.forName(charset.getSelectedItemPosition()==0?"UTF-8":"windows-1251");}
    private CallsignDatabase parse(File file,CallsignDatabase.Options opts,Charset encoding)throws IOException {
        try(Reader r=new InputStreamReader(new FileInputStream(file),encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))){return CallsignDatabase.read(r,opts);}
    }
    private void load(File file,CallsignDatabase.Options opts,Charset encoding)throws IOException {
        loaded(parse(file,opts,encoding));
    }
    private void loaded(CallsignDatabase db){runOnUiThread(()->{setRadioSource(false);database=db;setBusy(false);updateSummary();showPreview();});}
    private void updateSummary(){if(database==null)return;String detail=radioSource?"Прочитано непосредственно из MD-9600":"Строк CSV: "+database.sourceRows+"; пропущено: "+database.skipped+"; повторов: "+database.duplicates;summary.setText("Записей: "+database.entries.size()+" / "+CallsignDatabase.capacity(database.chars)+"\n"+detail+"\nДлина записи: "+database.chars+" символов. "+sourceLabel);}
    private void apply(){try{setRadioSource(false);CallsignDatabase.Options o=options();Charset c=selectedCharset();persistOptions();database=null;setBusy(true);worker.execute(()->{try{load(sourceFile(),o,c);}catch(Exception e){finishTask("Ошибка CSV: "+e.getMessage());}});}catch(Exception e){log(e.getMessage());}}
    private void openCsv(){setRadioSource(false);Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(i,1);}
    private void download(){
        try{setRadioSource(false);CallsignDatabase.Options o=options();persistOptions();charset.setSelection(0);database=null;setBusy(true);log("Загрузка RadioID.net...");
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
    private void showPreview(){if(database==null)return;String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);List<String> text=new ArrayList<>();visibleEntries.clear();int matches=0;for(CallsignDatabase.Entry e:database.entries){if(query.isEmpty()||Integer.toString(e.id).contains(query)||e.text.toLowerCase(Locale.ROOT).contains(query)){matches++;if(text.size()<200){visibleEntries.add(e);text.add(e.id+"   "+e.text+(e.encoded.length()<e.text.length()?"  →  "+e.encoded+"…":""));}}}preview.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,text));previewSummary.setText("Найдено: "+matches+". Показано: "+text.size()+". Нажмите запись для изменения. При записи используются все "+database.entries.size()+" записей подготовленной базы.");}
    private void editEntry(CallsignDatabase.Entry existing){
        if(database==null){log("Сначала прочитайте базу из рации или загрузите CSV.");return;}
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);int pad=(int)(12*getResources().getDisplayMetrics().density);box.setPadding(pad,0,pad,0);
        label(box,"DMR ID",14);
        EditText id=new EditText(this);id.setSingleLine(true);id.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);id.setHint("DMR ID");box.addView(id);
        boolean structured=existing==null||existing.details!=null;
        EditText[] parts=new EditText[5];EditText value=new EditText(this);
        if(structured){
            label(box,"Позывной обязателен. Остальные поля можно оставить пустыми.",14);
            String[] names={"Позывной","Имя","Город","Область","Страна"};
            for(int i=0;i<5;i++){label(box,names[i],14);parts[i]=new EditText(this);parts[i].setSingleLine(true);parts[i].setHint(names[i]);box.addView(parts[i]);if(existing!=null)parts[i].setText(existing.details[i]);}
        }else{
            label(box,"В рации поля хранятся одной строкой. Границы имени, города, области и страны не сохранены; строка редактируется целиком.",14);
            value.setHint("Позывной и дополнительные данные");value.setMinLines(2);value.setMaxLines(5);box.addView(value);value.setText(existing.text);
        }
        if(existing!=null)id.setText(Integer.toString(existing.id));
        TextView encoded=label(box,"",14);
        Runnable update=()->{try{
            String[] data=new String[5];if(structured)for(int i=0;i<5;i++)data[i]=parts[i].getText().toString();
            CallsignDatabase.Entry entry=structured?CallsignDatabase.structuredReplacement(id.getText().toString(),data,database.chars,separator.getSelectedItemPosition()==0?" ":".",existing):CallsignDatabase.manualEntry(id.getText().toString(),value.getText().toString(),database.chars);
            encoded.setText("Строка для рации ("+database.chars+" символов):\n"+entry.encoded+(entry.encoded.length()<entry.text.length()?"…":""));
        }catch(Exception e){encoded.setText(e.getMessage());}};
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence t,int st,int count,int after){}public void onTextChanged(CharSequence t,int st,int before,int count){update.run();}public void afterTextChanged(Editable e){}};
        id.addTextChangedListener(watcher);if(structured)for(EditText part:parts)part.addTextChangedListener(watcher);else value.addTextChangedListener(watcher);update.run();
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        String title=existing==null?"Добавить запись":"Изменить запись";
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null);
        if(existing!=null)builder.setNeutralButton("Удалить",null);
        AlertDialog dialog=builder.create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try {
                    String[] data=new String[5];if(structured)for(int i=0;i<5;i++)data[i]=parts[i].getText().toString();
                    CallsignDatabase.Entry next=structured?CallsignDatabase.structuredReplacement(id.getText().toString(),data,database.chars,separator.getSelectedItemPosition()==0?" ":".",existing):CallsignDatabase.manualEntry(id.getText().toString(),value.getText().toString(),database.chars);
                    database=database.withEntry(existing==null?null:existing.id,next);sourceLabel+=" • есть локальные правки";updateSummary();showPreview();setBusy(false);status.setText("Изменение сохранено только в подготовленной базе. Нажмите «Записать базу в рацию», чтобы применить его.");dialog.dismiss();
                }catch(Exception e){encoded.setText(e.getMessage());}
            });
            if(existing!=null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Удалить запись?").setMessage("ID "+existing.id+" будет удалён только из подготовленной базы. В рацию изменения попадут после отдельной записи.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{
                try{database=database.withoutEntry(existing.id);sourceLabel+=" • есть локальные правки";updateSummary();showPreview();setBusy(false);status.setText("Запись удалена из подготовленной базы. В рации она пока не изменена.");dialog.dismiss();}
                catch(Exception e){log("Ошибка удаления: "+e.getMessage());}
            }).show());
        });
        dialog.show();
    }
    private void confirm(boolean clear){
        if(!clear&&database==null)return;
        writeDatabase=clear?CallsignDatabase.empty(CallsignDatabase.LENGTHS[length.getSelectedItemPosition()]):database;
        String text=clear?"Удалить все записи базы позывных из рации?":"Заменить базу позывных в рации?\nЗаписей: "+writeDatabase.entries.size()+"\nСимволов: "+writeDatabase.chars;
        new AlertDialog.Builder(this).setTitle(clear?"Очистить базу позывных?":"Записать базу позывных?").setMessage(text+"\n\nПеред записью будет сохранена резервная копия, каждый записанный сектор будет проверен чтением. Не отключайте USB и питание до завершения.")
            .setNegativeButton("Отмена",null).setPositiveButton("Записать",(d,w)->begin()).show();
    }
    private void begin(){startUsbOperation(true);}
    private void readFromRadio(){if(database==null){startUsbOperation(false);return;}new AlertDialog.Builder(this).setTitle("Прочитать базу из рации?").setMessage("Текущая подготовленная база в редакторе будет заменена данными из MD-9600. Чтение ничего не записывает в рацию.").setNegativeButton("Отмена",null).setPositiveButton("Прочитать",(d,w)->startUsbOperation(false)).show();}
    private void startUsbOperation(boolean writing){setBusy(true);pendingWrite=writing;UsbDevice d=transport.findDevice();if(d==null){finishTask("MD-9600 не найдена по USB");return;}if(manager.hasPermission(d)){perform(d);return;}
        pendingDevice=d;permissionPending=true;Intent i=new Intent(PERMISSION).setPackage(getPackageName());PendingIntent pi=PendingIntent.getBroadcast(this,7,i,Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
        try{manager.requestPermission(d,pi);}catch(Exception e){permissionPending=false;pendingDevice=null;finishTask(e.getMessage());}}
    @Override protected void onResume(){super.onResume();if(permissionPending&&pendingDevice!=null&&manager.hasPermission(pendingDevice)){UsbDevice d=pendingDevice;permissionPending=false;pendingDevice=null;perform(d);}}
    private void perform(UsbDevice device){
        final boolean writing=pendingWrite;
        worker.execute(()->{
            boolean entered=false;final boolean[] started={false};String result;CallsignDatabase loadedFromRadio=null;
            try{
                transport.open(device);OpenGd77Protocol.FirmwareInfo info=protocol.readFirmwareInfo();CallsignWritePlan.checkRadio(info.radioType,info.structVersion,info.flashId);
                entered=true;protocol.enterProgrammingMode(writing,writing?"Writing DMR IDs":"Reading DMR IDs");
                if(writing){
                    CallsignWritePlan.Memory memory=new CallsignWritePlan.Memory(){public byte[] read(int a,int n)throws IOException{return protocol.readFlash(a,n);}public void write(int a,byte[] b)throws IOException{started[0]=true;protocol.writeFlashSector(a,b);}};
                    List<CallsignWritePlan.Sector> plan=CallsignWritePlan.prepare(writeDatabase,memory,this::log);
                    CallsignWritePlan.execute(plan,memory,s->saveBackup(s,info),this::log);
                    protocol.closeProgrammingMode();entered=false;
                    result="База обработана. Проверено секторов: "+plan.size()+".";
                    try{protocol.reboot();result+=" Рация перезагружается.";}catch(IOException e){result+=" Перезапустите рацию вручную.";}
                }else{
                    CallsignRadioReader.Memory memory=(a,n,p)->protocol.readFlashLarge(a,n,p);
                    loadedFromRadio=CallsignRadioReader.read(memory,this::log);
                    protocol.closeProgrammingMode();entered=false;
                    result="База прочитана. Запись в рацию не выполнялась.";
                }
            }catch(Exception e){result="Ошибка: "+e.getMessage()+(writing&&started[0]?"\nЗапись могла выполниться частично. Резервная копия сохранена. Перезапустите рацию и повторно запишите подготовленную базу.":writing?"\nЗапись базы не выполнялась.":"\nИзменения в рацию не записывались.");}
            finally{if(entered)try{protocol.closeProgrammingMode();}catch(Exception ignored){}transport.close();}
            final CallsignDatabase readResult=loadedFromRadio;final String finalResult=result;
            if(readResult!=null)runOnUiThread(()->{
                database=readResult;sourceLabel="Прочитано из MD-9600";setRadioSource(true);
                for(int i=0;i<CallsignDatabase.LENGTHS.length;i++)if(CallsignDatabase.LENGTHS[i]==readResult.chars){length.setSelection(i);break;}
                updateSummary();setBusy(false);showPreview();status.setText(finalResult);
            });else finishTask(finalResult);
        });
    }
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
    private void hideEditorKeyboard(){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);}
    private void openEditor(){if(database==null||busy)return;editorOpen=true;formPage.setVisibility(View.GONE);editorPage.setVisibility(View.VISIBLE);showPreview();}
    private void closeEditor(){if(busy)return;hideEditorKeyboard();editorOpen=false;editorPage.setVisibility(View.GONE);formPage.setVisibility(View.VISIBLE);}
    private void leave(){if(!busy)finish();}
    @Override public void onBackPressed(){if(editorOpen)closeEditor();else leave();}
    @Override protected void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception ignored){}if(!busy){transport.close();worker.shutdown();}super.onDestroy();}
}
