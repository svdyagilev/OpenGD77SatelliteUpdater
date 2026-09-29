package ru.opengd77.satupdate;

import android.app.*;
import android.content.*;
import android.hardware.usb.*;
import android.os.*;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Owns the USB connection for one explicit write session. No automatic retries. */
public class CodeplugWriteActivity extends Activity {
    private static final String PERMISSION="ru.opengd77.satupdate.USB_PERMISSION_WRITE";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private UsbManager manager;private UsbCdcSerialTransport transport;private OpenGd77Protocol protocol;
    private CodeplugProject project;private CodeplugWritePlan plan;
    private TextView status;private Button write,back;private CheckBox[] sections;
    private volatile boolean busy,permissionPending;private boolean started,succeeded;
    private UsbDevice pendingDevice;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            if(!PERMISSION.equals(i.getAction())||!permissionPending)return;
            UsbDevice d=pendingDevice;permissionPending=false;pendingDevice=null;
            if(d!=null&&manager.hasPermission(d))perform(d);else finishOperation("Доступ к USB не разрешён.");
        }
    };
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);
        TextView title=new TextView(this);title.setText("Запись изменений • MD-9600");title.setTextSize(22);body.addView(title);
        sections=new CheckBox[CodeplugWritePlan.NAMES.length];
        for(int i=0;i<sections.length;i++){sections[i]=new CheckBox(this);body.addView(sections[i]);}
        write=new Button(this);write.setText("Записать выбранные разделы");body.addView(write);
        Button backup=new Button(this);backup.setText("Сохранить последнюю резервную копию");body.addView(backup);
        ScrollView scroll=new ScrollView(this);status=new TextView(this);status.setTextSize(15);scroll.addView(status);
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        back=new Button(this);back.setText("Назад");body.addView(back);setContentView(body);
        manager=(UsbManager)getSystemService(USB_SERVICE);transport=new UsbCdcSerialTransport(manager);protocol=new OpenGd77Protocol(transport);
        IntentFilter filter=new IntentFilter(PERMISSION);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,filter);
        back.setOnClickListener(v->leave());write.setOnClickListener(v->confirm());backup.setOnClickListener(v->exportBackup());
        try{
            project=CodeplugSession.project;
            if(project==null){project=CodeplugProjectStore.load(this);CodeplugSession.install(project);}
            CodeplugWritePlan all=new CodeplugWritePlan(project,new boolean[]{true,true,true,true,true});
            for(int i=0;i<sections.length;i++){
                sections[i].setText(CodeplugWritePlan.NAMES[i]+" — "+all.counts[i]+" байт");
                sections[i].setEnabled(all.counts[i]>0);sections[i].setChecked(all.counts[i]>0);
            }
            write.setEnabled(!all.changes.isEmpty()&&!CodeplugWriteBackup.pending(this));
            log("Выберите один или несколько разделов. Перед записью сохраняется резервная копия, после — проверяются все записанные секторы.");
            log("Во время записи не отключайте USB и питание рации. По завершении рация перезагрузится.");
            if(all.changes.isEmpty())log("В проекте нет изменений для записи.");
            if(CodeplugWriteBackup.pending(this))log("Предыдущая запись не завершена. Сохраните проект и резервную копию, затем заново прочитайте рацию. Повторная запись пока заблокирована.");
        }catch(Exception e){write.setEnabled(false);log(e.getMessage());}
    }
    private void confirm(){
        if(busy||succeeded||CodeplugWriteBackup.pending(this))return;
        try{
            boolean[] selected=new boolean[sections.length];for(int i=0;i<selected.length;i++)selected[i]=sections[i].isChecked();
            plan=new CodeplugWritePlan(project,selected);
            if(plan.changes.isEmpty())throw new IOException("Выберите раздел с изменениями");
            new AlertDialog.Builder(this).setTitle("Записать в MD-9600?")
                .setMessage(plan.summary()+"\nВсего: "+plan.changes.size()+" изменённых байт.\nПроверьте, что подключена нужная рация.")
                .setNegativeButton("Отмена",null).setPositiveButton("Записать",(d,w)->begin()).show();
        }catch(Exception e){log(e.getMessage());}
    }
    private void begin(){
        if(busy||CodeplugWriteBackup.pending(this))return;
        busy=true;write.setEnabled(false);back.setEnabled(false);for(CheckBox box:sections)box.setEnabled(false);
        UsbDevice d=transport.findDevice();if(d==null){finishOperation("Радиостанция не найдена по USB.");return;}
        if(manager.hasPermission(d)){perform(d);return;}
        permissionPending=true;pendingDevice=d;
        Intent intent=new Intent(PERMISSION).setPackage(getPackageName());
        PendingIntent pi=PendingIntent.getBroadcast(this,1,intent,Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
        try{manager.requestPermission(d,pi);}catch(Exception e){permissionPending=false;pendingDevice=null;finishOperation(e.getMessage());}
    }
    @Override protected void onResume(){
        super.onResume();
        if(permissionPending&&pendingDevice!=null&&manager.hasPermission(pendingDevice)){
            UsbDevice d=pendingDevice;pendingDevice=null;permissionPending=false;perform(d);
        }
    }
    private void perform(UsbDevice device){
        worker.execute(()->{
            boolean entered=false;
            try{
                transport.open(device);
                RadioDriver.Identity id=new Md9600Driver(protocol).identify();
                if(!id.firmware.equals(project.identity.firmware)||id.infoVersion!=project.identity.infoVersion)
                    throw new IOException("Прошивка отличается от проекта. Сначала прочитайте рацию заново.");
                log(id.compactText());entered=true;protocol.enterProgrammingMode(true,"Writing codeplug");
                plan.execute(new CodeplugWritePlan.Memory(){
                    public byte[] read(int address,int length)throws IOException{return protocol.readFlash(address,length);}
                    public void write(int address,byte[] data)throws IOException{protocol.writeFlashSector(address,data);}
                },sectors->{CodeplugWriteBackup.save(this,plan,sectors);started=true;log("Резервная копия сохранена. Начинаю запись.");},this::log);
                CodeplugProject next=plan.completedProject();CodeplugProjectStore.save(this,next);CodeplugSession.install(next);
                CodeplugWriteBackup.clear(this);succeeded=true;
                log("Запись завершена. Все изменённые секторы проверены повторным чтением.");
                try{protocol.closeProgrammingMode();entered=false;protocol.reboot();log("Команда перезагрузки отправлена.");}
                catch(IOException e){entered=false;log("Данные записаны и проверены. Перезапустите рацию вручную: "+e.getMessage());}
                log("Готово. Невыбранные правки остались в проекте.");
            }catch(Exception e){
                log("Ошибка: "+e.getMessage());
                if(started||CodeplugWriteBackup.pending(this))log("Запись могла выполниться частично. Автоматического повтора нет. Сохраните резервную копию, перезапустите рацию и заново прочитайте её перед следующей записью.");
                log(started?"Запись остановлена.":"Запись изменений не выполнена.");
            }finally{
                if(entered)try{protocol.closeProgrammingMode();}catch(Exception ignored){}
                try{transport.close();}catch(Exception ignored){}
                finishOperation("USB-сеанс завершён.");
                worker.shutdown();
            }
        });
    }
    private void finishOperation(String text){log(text);runOnUiThread(()->{
        busy=false;back.setEnabled(true);
        // One attempt per screen; return and reopen for preflight failures.
        write.setEnabled(false);
    });}
    private void log(String text){runOnUiThread(()->status.append((status.length()==0?"":"\n")+text));}
    private void leave(){if(busy)return;startActivity(new Intent(this,CodeplugViewerActivity.class));finish();}
    @Override public void onBackPressed(){leave();}
    private void exportBackup(){
        if(busy)return;
        File file=CodeplugWriteBackup.latest(this);if(file==null){log("Резервных копий записи пока нет.");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE,file.getName());startActivityForResult(i,1);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=1||result!=RESULT_OK||data==null||data.getData()==null)return;
        File file=CodeplugWriteBackup.latest(this);if(file==null){log("Резервная копия не найдена.");return;}
        try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){
            if(out==null)throw new IOException("Не удалось открыть файл");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();log("Резервная копия экспортирована.");
        }catch(Exception e){log("Ошибка экспорта: "+e.getMessage());}
    }
    @Override protected void onDestroy(){
        try{unregisterReceiver(receiver);}catch(Exception ignored){}
        // Rotation is handled in-place. Never deliberately close USB halfway through a sector.
        if(!busy){transport.close();worker.shutdown();}
        super.onDestroy();
    }
}
