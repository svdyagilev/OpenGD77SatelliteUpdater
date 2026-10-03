package ru.opengd77.satupdate;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CodeplugViewerActivity extends ScreenActivity {
    private CodeplugModel model;
    private int activeCategory;
    private static final int OPEN_PROJECT = 601, SAVE_PROJECT = 602, IMPORT_CHANNELS = 603, EXPORT_CHANNELS = 604, COMPARE_PROJECT = 605, RESTORE_BACKUP = 606;
    private final java.util.concurrent.ExecutorService toolsWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private byte[] pendingExport;
    private android.app.ProgressDialog toolsProgress;
    private Button navigationButton;
    private boolean searching;
    private EditText projectSearch;
    private ListView listView;
    private TextView summaryText;
    private final List<Object> visibleObjects = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_codeplug_viewer);

        if (CodeplugSession.project == null) {
            try { CodeplugSession.install(CodeplugProjectStore.load(this)); }
            catch (Exception e) { new AlertDialog.Builder(this).setTitle("Проект не открыт")
                    .setMessage(e.getMessage()).setPositiveButton("OK", null).show(); }
        }
        model = CodeplugSession.current;
        summaryText = findViewById(R.id.codeplugSummaryText);
        navigationButton=findViewById(R.id.codeplugNavigationButton);
        navigationButton.setOnClickListener(v -> navigationMenu());
        projectSearch=findViewById(R.id.projectSearchEdit);
        listView = findViewById(R.id.codeplugList);
        findViewById(R.id.codeplugCloseButton).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.projectSearchButton).setOnClickListener(v -> { searchProject(); hideKeyboard(); });

        findViewById(R.id.editorMenuButton).setOnClickListener(v -> editorMenu());
        findViewById(R.id.searchToggleButton).setOnClickListener(v -> toggleSearch());
        projectSearch.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) { searchProject(); hideKeyboard(); return true; }
            return false;
        });
        findViewById(R.id.editGeneralButton).setOnClickListener(v -> {
            if (model == null) return;
            if (activeCategory == 10) CodeplugEditDialogs.boot(this, model.boot, this::commitEdit);
            else if(activeCategory==9)CodeplugEditDialogs.dtmfSettings(this,model.dtmfSettings,this::commitEdit);
            else if(activeCategory==14)CodeplugEditDialogs.bands(this,model,this::commitEdit);
            else if(activeCategory==16)CodeplugEditDialogs.radio(this,model,this::commitEdit);
            else CodeplugEditDialogs.general(this, model.general, this::commitEdit);
        });
        findViewById(R.id.addRecordButton).setOnClickListener(v -> {
            try { if (creationKind()!=null && CodeplugSession.project!=null)
                CodeplugEditDialogs.create(this,CodeplugSession.project,creationKind(),this::commitEdit);
            } catch(Exception e) { problem(e); }
        });
        refreshSummary();

        if (savedInstanceState != null) {
            activeCategory=savedInstanceState.getInt("category",0);
            projectSearch.setText(savedInstanceState.getString("query",""));
            searching=savedInstanceState.getBoolean("searching",false);
            findViewById(R.id.projectSearchRow).setVisibility(searching?View.VISIBLE:View.GONE);
            ((Button)findViewById(R.id.searchToggleButton)).setText(searching?"Закрыть":"Поиск");
        }
        showCategory(activeCategory);
        if(searching)searchProject();
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if(position>=visibleObjects.size())return;
            Object item=visibleObjects.get(position);
            if(item instanceof CodeplugNavigation.Link)showCategory(((CodeplugNavigation.Link)item).page);
            else showDetails(item);
        });
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("category",activeCategory);
        out.putString("query",projectSearch.getText().toString());out.putBoolean("searching",searching);
    }

    @Override public void onBackPressed() {
        if(searching){toggleSearch();return;}
        int parent=CodeplugNavigation.parent(activeCategory);
        if(parent<0)returnToMain();else showCategory(parent);
    }

    private void hideKeyboard() {
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(projectSearch.getWindowToken(),0);
    }

    private void toggleSearch() {
        searching=!searching;
        findViewById(R.id.projectSearchRow).setVisibility(searching?View.VISIBLE:View.GONE);
        ((Button)findViewById(R.id.searchToggleButton)).setText(searching?"Закрыть":"Поиск");
        if(searching){
            projectSearch.requestFocus();
            ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(projectSearch,InputMethodManager.SHOW_IMPLICIT);
        }else{
            projectSearch.setText("");hideKeyboard();showCategory(activeCategory);
        }
    }

    private void editorMenu() {
        String[] items={"Проект: открыть, сохранить, отменить","Записать в радиостанцию","База позывных","Сведения о проекте","Инструменты каналов и зон"};
        new AlertDialog.Builder(this).setTitle("Действия").setItems(items,(dialog,which)->{
            if(which==0)projectMenu();
            else if(which==1){
                if(model==null){problem(new IllegalStateException("Сначала откройте или прочитайте проект"));return;}
                startActivity(new Intent(this,CodeplugWriteActivity.class));finish();
            }else if(which==2)startActivity(new Intent(this,CallsignActivity.class));
            else if(which==4)channelTools();
            else if(model!=null&&CodeplugSession.project!=null)new AlertDialog.Builder(this).setTitle("Сведения о проекте")
                    .setMessage(model.general.radioName+" • DMR ID "+model.general.dmrId+"\n"+model.compactSummary()
                            +"\nИзменено байтов: "+CodeplugSession.project.changedBytes()+"\nПравки автоматически сохраняются на телефоне.")
                    .setPositiveButton("OK",null).show();
        }).show();
    }

    private void navigationMenu() {
        int[] pages={0,100,101,102,103};
        String[] names=new String[pages.length];
        for(int i=0;i<pages.length;i++)names[i]=CodeplugNavigation.title(pages[i]);
        new AlertDialog.Builder(this).setTitle("Обзор и разделы").setItems(names,(dialog,which)->{
            if(searching)toggleSearch();
            showCategory(pages[which]);
        }).setNegativeButton("Закрыть",null).show();
    }

    private String sectionDetail(int page) {
        switch(page){
            case 100:return "Позывной, параметры рации, загрузочный экран";
            case 101:return model.channels.size()+" каналов • "+model.zones.size()+" зон";
            case 102:return model.contacts.size()+" DMR • "+model.dtmfContacts.size()+" DTMF";
            case 103:return model.aprsConfigs.size()+" APRS • "+model.satellites.size()+" спутников";
            case 16:return "Чувствительность VOX: "+model.general.voxSense;
            case 14:return model.deviceInfo.vhfRangeText()+" • "+model.deviceInfo.uhfRangeText();
            default:return "";
        }
    }

    private void returnToMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void addText(List<String> rows, String text) {
        rows.add(text);
        visibleObjects.add(text);
    }

    private CodeplugRecords.Kind creationKind() {
        switch(activeCategory){
            case 1:return CodeplugRecords.Kind.CHANNEL;
            case 3:return CodeplugRecords.Kind.ZONE;
            case 4:return CodeplugRecords.Kind.DMR;
            case 8:return CodeplugRecords.Kind.DTMF;
            case 5:return CodeplugRecords.Kind.GROUP;
            case 6:return CodeplugRecords.Kind.SCAN;
            case 7:return CodeplugRecords.Kind.APRS;
            default:return null;
        }
    }
    private void refreshAddButton() {
        android.widget.Button button=findViewById(R.id.addRecordButton);
        CodeplugRecords.Kind kind=creationKind();
        button.setVisibility(model!=null && kind!=null?View.VISIBLE:View.GONE);
        button.setText("+ Добавить");
    }

    private void showCategory(int category) {
        activeCategory = category;
        navigationButton.setText(CodeplugNavigation.title(category)+" ▾");
        refreshSummary();
        refreshAddButton();
        visibleObjects.clear();
        findViewById(R.id.editGeneralButton).setVisibility(model != null && (category == 13 || category == 10 || category == 9 || category == 14 || category == 16) ? View.VISIBLE : View.GONE);
        if (model == null) return;
        List<String> rows = new ArrayList<>();
        int[] children=CodeplugNavigation.children(category);
        if(children.length>0){
            for(int child:children){
                String detail=sectionDetail(child);
                rows.add(CodeplugNavigation.title(child)+"  ›"+(detail.isEmpty()?"":"\n"+detail));
                visibleObjects.add(new CodeplugNavigation.Link(child));
            }
            listView.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,rows));
            return;
        }
        switch (category) {
            case 1:
                for (CodeplugModel.Channel c : model.channels) {
                    visibleObjects.add(c); rows.add(c.oneLine());
                }
                break;
            case 2:
                for (CodeplugModel.Channel c : model.vfos) {
                    visibleObjects.add(c); rows.add(c.oneLine());
                }
                break;
            case 3:
                for (CodeplugModel.Zone z : model.zones) {
                    visibleObjects.add(z); rows.add(z.oneLine());
                }
                break;
            case 4:
                for (CodeplugModel.Contact c : model.contacts) {
                    visibleObjects.add(c); rows.add(c.oneLine());
                }
                break;
            case 5:
                for (CodeplugModel.RxGroup g : model.rxGroups) {
                    visibleObjects.add(g); rows.add(g.oneLine());
                }
                break;
            case 6:
                for (CodeplugModel.ScanList s : model.scanLists) {
                    visibleObjects.add(s); rows.add(s.oneLine());
                }
                break;
            case 7:
                for (CodeplugModel.AprsConfig a : model.aprsConfigs) {
                    visibleObjects.add(a); rows.add(a.oneLine());
                }
                break;
            case 8:
                for (CodeplugModel.DtmfContact d : model.dtmfContacts) {
                    visibleObjects.add(d); rows.add(d.oneLine());
                }
                break;
            case 9:
                showDtmfSettings(rows);
                break;
            case 10:
                showBoot(rows);
                break;
            case 11:
                showDeviceInfo(rows);
                break;
            case 12:
                for (CodeplugModel.Satellite s : model.satellites) {
                    visibleObjects.add(s); rows.add(s.oneLine());
                }
                if (rows.isEmpty()) addText(rows, "Данные спутников (TLV) не найдены или пусты");
                break;
            case 13:
                addText(rows, "DMR ID: " + model.general.dmrId);
                addText(rows, "Позывной / имя станции: " + emptyDash(model.general.radioName));
                break;
            case 14:
                addText(rows, "Границы UHF: " + model.deviceInfo.uhfRangeText());
                addText(rows, "Границы VHF: " + model.deviceInfo.vhfRangeText());
                addText(rows, "Границы прочитаны из codeplug и могут отличаться от текущих ограничений передачи в прошивке.");
                break;
            case 16:
                addText(rows, "Чувствительность VOX: " + model.general.voxSense);
                addText(rows, String.format(Locale.US, "Общие флаги (HEX): %02X %02X %02X %02X",
                        model.general.flag1, model.general.flag2, model.general.flag3, model.general.flag4));
                break;
        }
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
    }

    private void searchProject() {
        String query=projectSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        if(query.isEmpty()){refreshSummary();showCategory(activeCategory);return;}
        if(model==null)return;
        findViewById(R.id.addRecordButton).setVisibility(View.GONE);
        findViewById(R.id.editGeneralButton).setVisibility(View.GONE);
        List<String> rows=new ArrayList<>();visibleObjects.clear();
        for(CodeplugModel.Channel c:model.channels)searchEntry(rows,c,"Канал",c.name+" "+c.optionalDmrId+" "+c.colorCode+" "+c.timeSlot,query);
        for(int i=0;i<model.vfos.size();i++)searchEntry(rows,model.vfos.get(i),"VFO "+(i==0?"A":"B"),model.vfos.get(i).name,query);
        for(CodeplugModel.Zone z:model.zones)searchEntry(rows,z,"Зона",z.channelIndices.toString(),query);
        for(CodeplugModel.Contact c:model.contacts)searchEntry(rows,c,"Контакт DMR",c.name+" "+c.number+" "+c.typeText(),query);
        for(CodeplugModel.DtmfContact c:model.dtmfContacts)searchEntry(rows,c,"Контакт DTMF",c.name+" "+c.code,query);
        for(CodeplugModel.RxGroup g:model.rxGroups)searchEntry(rows,g,"Группа приёма",g.contactIndices.toString(),query);
        for(CodeplugModel.ScanList s:model.scanLists)searchEntry(rows,s,"Список сканирования",s.channelIndices.toString(),query);
        for(CodeplugModel.AprsConfig a:model.aprsConfigs)searchEntry(rows,a,"APRS",a.name+" "+a.comment+" "+a.via1+" "+a.via2,query);
        for(CodeplugModel.Satellite s:model.satellites)searchEntry(rows,s,"Спутник",s.name,query);
        searchText(rows,"Позывной / имя станции",model.general.radioName,query);
        searchText(rows,"DMR ID",Long.toString(model.general.dmrId),query);
        searchText(rows,"Заставка, строка 1",model.boot.line1,query);
        searchText(rows,"Заставка, строка 2",model.boot.line2,query);
        listView.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,rows));
        summaryText.setText("Найдено: "+rows.size()+" • весь проект");
    }

    private void searchEntry(List<String> rows,Object item,String label,String extra,String query) {
        String line=oneLine(item);
        if((label+" "+line+" "+extra).toLowerCase(Locale.ROOT).contains(query)){
            visibleObjects.add(item);rows.add(label+" • "+line);
        }
    }

    private void searchText(List<String> rows,String label,String value,String query) {
        if(value!=null&&value.toLowerCase(Locale.ROOT).contains(query)){
            String line=label+": "+value;visibleObjects.add(line);rows.add(line);
        }
    }

    private String oneLine(Object item) {
        if(item instanceof CodeplugModel.Channel)return ((CodeplugModel.Channel)item).oneLine();
        if(item instanceof CodeplugModel.Zone)return ((CodeplugModel.Zone)item).oneLine();
        if(item instanceof CodeplugModel.Contact)return ((CodeplugModel.Contact)item).oneLine();
        if(item instanceof CodeplugModel.DtmfContact)return ((CodeplugModel.DtmfContact)item).oneLine();
        if(item instanceof CodeplugModel.RxGroup)return ((CodeplugModel.RxGroup)item).oneLine();
        if(item instanceof CodeplugModel.ScanList)return ((CodeplugModel.ScanList)item).oneLine();
        if(item instanceof CodeplugModel.AprsConfig)return ((CodeplugModel.AprsConfig)item).oneLine();
        if(item instanceof CodeplugModel.Satellite)return ((CodeplugModel.Satellite)item).oneLine();
        return item.toString();
    }

    private void showDtmfSettings(List<String> rows) {
        CodeplugModel.DtmfSettings d = model.dtmfSettings;
        addText(rows, "Собственный ID: " + emptyDash(d.selfId));
        addText(rows, "Код блокировки: " + emptyDash(d.killCode));
        addText(rows, "Код разблокировки: " + emptyDash(d.wakeCode));
        addText(rows, "Разделитель (код): " + d.delimiter + " • Групповой символ (код): " + d.groupCode);
        addText(rows, "Ответ декодера (код): " + d.decodeResponse + " • Автосброс: " + d.autoResetSeconds + " с");
        addText(rows, "Декодирование блокировки/разблокировки: " + yesNo(d.killWakeDecode) + " • Тип блокировки (код): " + d.killType);
        addText(rows, "Код при нажатии PTT: " + emptyDash(d.pttUp));
        addText(rows, "Код при отпускании PTT: " + emptyDash(d.pttDown));
        addText(rows, String.format(Locale.US, "Удержание ответа: %.1f с • Декодирование: %.1f с",
                d.responseHoldSeconds, d.decodeTimeSeconds));
        addText(rows, "Задержка первого символа: " + d.firstDigitDelayMs + " мс");
        addText(rows, "Длительность первого/остальных символов: " + d.firstDigitDurationMs + " / " + d.otherDurationMs + " мс");
        addText(rows, "Скорость (код): " + d.rate + " • Завершающая задержка: " + d.tailMs + " мс");
    }

    private void showBoot(List<String> rows) {
        CodeplugModel.BootInfo b = model.boot;
        addText(rows, "Заставка: " + (b.introMode == 1 ? "Текст" : b.introMode == 0 ? "Изображение" : "Код " + b.introMode));
        addText(rows, "Строка 1: " + emptyDash(b.line1));
        addText(rows, "Строка 2: " + emptyDash(b.line2));
    }

    private void showDeviceInfo(List<String> rows) {
        CodeplugModel.DeviceInfo d = model.deviceInfo;
        addText(rows, "Модель (USB): " + available(d.liveModel));
        addText(rows, "Строка идентификации FW (USB): " + available(d.liveFirmware));
        addText(rows, "Модель из codeplug: " + stored(d.model));
        addText(rows, "Серийный номер: " + stored(d.serial));
        addText(rows, "Модель и строка идентификации FW получены по USB. Эта строка может не содержать номер версии. Остальные поля — из codeplug; "
                + "они могут быть пустыми или относиться к прежней прошивке.");
    }

    private void showDetails(Object obj) {
        if (obj instanceof String) return;
        String title;
        String message;
        if (obj instanceof CodeplugModel.Channel) {
            CodeplugModel.Channel c = (CodeplugModel.Channel)obj;
            title = (c.index > 0 ? "Канал " + c.index : "VFO") + " • " + c.name;
            message = channelDetails(c);
        } else if (obj instanceof CodeplugModel.Zone) {
            CodeplugModel.Zone z = (CodeplugModel.Zone)obj;
            title = "Зона " + z.index + " • " + z.name;
            message = memberChannels(z.channelIndices);
        } else if (obj instanceof CodeplugModel.Contact) {
            CodeplugModel.Contact c = (CodeplugModel.Contact)obj;
            title = "Контакт " + c.index + " • " + c.name;
            message = "Тип: " + c.typeText() + "\nID/TG: " + c.number
                    + "\nПереопределение таймслота: " + (c.tsOverride == 0x00 ? "TS1" : c.tsOverride == 0x02 ? "TS2" : "нет");
        } else if (obj instanceof CodeplugModel.RxGroup) {
            CodeplugModel.RxGroup g = (CodeplugModel.RxGroup)obj;
            title = "Группа приёма " + g.index + " • " + g.name;
            StringBuilder b = new StringBuilder();
            for (int idx : g.contactIndices) b.append(refContact(idx)).append('\n');
            message = b.length() == 0 ? "Контактов нет" : b.toString().trim();
        } else if (obj instanceof CodeplugModel.ScanList) {
            CodeplugModel.ScanList s = (CodeplugModel.ScanList)obj;
            title = "Список сканирования " + s.index + " • " + s.name;
            message = memberChannels(s.channelIndices)
                    + "\n\nПриоритет 1: " + refChannel(s.primary)
                    + "\nПриоритет 2: " + refChannel(s.secondary)
                    + "\nКанал ответа: " + refChannel(s.revert);
        } else if (obj instanceof CodeplugModel.AprsConfig) {
            CodeplugModel.AprsConfig a = (CodeplugModel.AprsConfig)obj;
            title = "APRS " + a.index + " • " + a.name;
            message = aprsDetails(a);
        } else if (obj instanceof CodeplugModel.DtmfContact) {
            CodeplugModel.DtmfContact d = (CodeplugModel.DtmfContact)obj;
            title = "DTMF " + d.index + " • " + d.name;
            message = "Код: " + emptyDash(d.code);
        } else if (obj instanceof CodeplugModel.Satellite) {
            CodeplugModel.Satellite s = (CodeplugModel.Satellite)obj;
            title = "Спутник • " + s.name;
            message = Double.isNaN(s.ageDays) ? "Эпоха: неизвестен"
                    : String.format(Locale.US, "Возраст Keps: %.2f дн.", s.ageDays);
        } else {
            return;
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("Закрыть", null);
        if (CodeplugSession.project != null && CodeplugEditDialogs.supported(obj)) {
            dialog.setNeutralButton(obj instanceof CodeplugModel.Channel&&((CodeplugModel.Channel)obj).index>0||obj instanceof CodeplugModel.Zone?"Действия":"Изменить", (d, w) -> {
                if(obj instanceof CodeplugModel.Channel&&((CodeplugModel.Channel)obj).index>0||obj instanceof CodeplugModel.Zone)recordActions(obj);
                else CodeplugEditDialogs.edit(this,obj,model,this::commitEdit);
            });
        }
        if(CodeplugSession.project!=null&&CodeplugRecords.kind(obj)!=null)dialog.setNegativeButton("Удалить",(d,w)->confirmDelete(obj,title));
        dialog.show();
    }
    private void recordActions(Object item) {
        boolean channel=item instanceof CodeplugModel.Channel;
        String[] actions=channel?new String[]{"Изменить","Копировать канал","Создать серию по шаблону"}
                :new String[]{"Изменить имя и состав","Копировать зону","Добавить и упорядочить каналы"};
        new AlertDialog.Builder(this).setTitle(channel?((CodeplugModel.Channel)item).name:((CodeplugModel.Zone)item).name)
            .setItems(actions,(dialog,which)->{
                if(which==0)CodeplugEditDialogs.edit(this,item,model,this::commitEdit);
                else if(which==1)copyRecord(item);
                else if(channel)CodeplugEditDialogs.series(this,(CodeplugModel.Channel)item,change->reviewChange("Создание серии каналов",change));
                else CodeplugEditDialogs.zoneOrder(this,(CodeplugModel.Zone)item,model,change->reviewChange("Изменение состава зоны",change));
            }).show();
    }

    private void copyRecord(Object item) {
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Имя копии (до 16 символов)");
        String original=item instanceof CodeplugModel.Channel?((CodeplugModel.Channel)item).name:((CodeplugModel.Zone)item).name;
        name.setText(original);name.selectAll();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Копировать: "+original).setView(name)
            .setNegativeButton("Отмена",null).setPositiveButton("Подготовить копию",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String value=name.getText().toString().trim();
            if(value.isEmpty()){name.setError("Укажите имя");return;}
            reviewChange("Копирование: "+value,snapshot->{
                if(item instanceof CodeplugModel.Channel)ChannelBatch.copyChannel(snapshot,((CodeplugModel.Channel)item).index,value,java.util.Collections.emptyMap());
                else ChannelBatch.copyZone(snapshot,((CodeplugModel.Zone)item).index,value);
            });dialog.dismiss();
        }));dialog.show();
    }

    private void channelTools() {
        if(model==null||CodeplugSession.project==null){problem(new IllegalStateException("Сначала откройте или прочитайте проект"));return;}
        String[] actions={"Массовая правка каналов","Серия по шаблону канала","Копировать канал","Копировать зону","Состав и порядок каналов зоны","Экспорт каналов в CSV","Импорт каналов из CSV"};
        new AlertDialog.Builder(this).setTitle("Каналы и зоны").setItems(actions,(dialog,which)->{
            if(which==0)selectBatchChannels();
            else if(which==1||which==2)pickRecord(false,item->{
                if(which==1)CodeplugEditDialogs.series(this,(CodeplugModel.Channel)item,change->reviewChange("Создание серии каналов",change));else copyRecord(item);
            });
            else if(which==3||which==4)pickRecord(true,item->{
                if(which==3)copyRecord(item);else CodeplugEditDialogs.zoneOrder(this,(CodeplugModel.Zone)item,model,change->reviewChange("Изменение состава зоны",change));
            });
            else if(which==5)exportChannels();else importChannels();
        }).show();
    }
    private interface Picked {void apply(Object item);}
    private void pickRecord(boolean zones,Picked picked) {
        List<Object> items=new ArrayList<>();if(zones)items.addAll(model.zones);else items.addAll(model.channels);
        if(items.isEmpty()){problem(new IllegalArgumentException(zones?"Зон пока нет":"Каналов пока нет"));return;}
        String[] labels=new String[items.size()];for(int i=0;i<labels.length;i++)labels[i]=oneLine(items.get(i));
        new AlertDialog.Builder(this).setTitle(zones?"Выберите зону":"Выберите исходный канал").setItems(labels,(dialog,which)->picked.apply(items.get(which))).setNegativeButton("Отмена",null).show();
    }
    private void selectBatchChannels() {
        List<CodeplugModel.Channel> channels=new ArrayList<>(model.channels);String[] labels=new String[channels.size()];boolean[] checked=new boolean[labels.length];
        for(int i=0;i<labels.length;i++)labels[i]=channels.get(i).oneLine();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Каналы для массовой правки").setMultiChoiceItems(labels,checked,(d,which,on)->checked[which]=on)
                .setNegativeButton("Отмена",null).setNeutralButton("Выбрать все",null).setPositiveButton("Далее",null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button->{
                boolean all=true;for(boolean value:checked)all&=value;
                for(int i=0;i<checked.length;i++){checked[i]=!all;dialog.getListView().setItemChecked(i,!all);}
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText(all?"Выбрать все":"Снять выбор");
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                List<Integer> ids=new ArrayList<>();int fm=0,dmr=0;
                for(int i=0;i<checked.length;i++)if(checked[i]){ids.add(channels.get(i).index);if(channels.get(i).digital)dmr++;else fm++;}
                if(ids.isEmpty()){problem(new IllegalArgumentException("Выберите хотя бы один канал"));return;}
                final String title="Массовая правка: "+ids.size()+" каналов (FM "+fm+", DMR "+dmr+")";
                CodeplugEditDialogs.bulkChannels(this,model,ids,change->reviewChange(title,change));dialog.dismiss();
            });
        });dialog.show();
    }
    private interface Operation {CodeplugProject apply(CodeplugProject base)throws Exception;}
    private void reviewChange(String title,CodeplugProject.Change change){reviewOperation(title,base->base.edit(change));}
    private void reviewOperation(String title,Operation operation) {
        CodeplugProject base=CodeplugSession.project;
        if(base==null){problem(new IllegalStateException("Нет открытого проекта"));return;}
        android.app.ProgressDialog progress=android.app.ProgressDialog.show(this,title,"Подготовка и проверка…",true,true);toolsProgress=progress;
        java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.Future<?> task=toolsWorker.submit(()->{
            try{
                CodeplugProject next=operation.apply(base);CodeplugIntegrity.masks(next);CodeplugIntegrity.links(next.original,next.working);
                CodeplugModel before=base.model(),after=next.model();int changed=0;
                byte[][] a=CodeplugProject.blocks(base.working),b=CodeplugProject.blocks(next.working);
                for(int block=0;block<a.length;block++)for(int offset=0;offset<a[block].length;offset++)if(a[block][offset]!=b[block][offset])changed++;
                final int changedBytes=changed;
                runOnUiThread(()->{
                    if(cancelled.get()||isFinishing()||isDestroyed())return;progress.dismiss();
                    if(changedBytes==0){new AlertDialog.Builder(this).setMessage("Изменений нет. Проверьте выбранные поля и режимы каналов.").setPositiveButton("OK",null).show();return;}
                    String summary="Будет добавлено каналов: "+(after.channels.size()-before.channels.size())+"\nБудет добавлено зон: "+(after.zones.size()-before.zones.size())
                            +"\nИзменено байтов: "+changedBytes+"\n\nПравки будут применены к проекту на телефоне и отменяются одним шагом через меню «Проект».";
                    ProjectReportDialogs.show(this,title,summary,new ProjectDiff(base.working,next.working),()->{
                        try{if(CodeplugSession.project!=base)throw new IllegalStateException("Проект изменился. Подготовьте операцию заново.");installProject(next);}
                        catch(Exception e){problem(e);}
                    });
                });
            }catch(Exception e){runOnUiThread(()->{if(cancelled.get()||isFinishing()||isDestroyed())return;progress.dismiss();problem(e);});}
        });progress.setOnCancelListener(dialog->{cancelled.set(true);task.cancel(true);});
    }
    private void exportChannels() {
        pendingExport=ChannelCsv.export(model).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv")
                .putExtra(Intent.EXTRA_TITLE,"MD9600-channels.csv");startActivityForResult(intent,EXPORT_CHANNELS);
    }
    private void importChannels() {
        new AlertDialog.Builder(this).setTitle("Импорт каналов CSV").setMessage("Каналы будут добавлены в свободные места. Существующие каналы сохраняются.\n\nUTF-8; обязательные столбцы: name, rx_mhz, tx_mhz, mode (FM/DMR). Можно использовать CSV из экспорта приложения. Ссылки на контакты, группы и APRS должны существовать в текущем проекте.")
            .setNegativeButton("Отмена",null).setPositiveButton("Выбрать CSV",(dialog,which)->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT_CHANNELS)).show();
    }
    @Override protected void onDestroy(){toolsWorker.shutdownNow();if(toolsProgress!=null)toolsProgress.dismiss();super.onDestroy();}

    private void confirmDelete(Object obj,String title){
        try{
            CodeplugProject current=CodeplugSession.project;
            CodeplugProject next=current.edit(s->CodeplugRecords.delete(s,current.original,CodeplugRecords.kind(obj),CodeplugRecords.index(obj)));
            StringBuilder impact=new StringBuilder();
            byte[][] before=CodeplugProject.blocks(current.working),after=CodeplugProject.blocks(next.working);
            for(int section=0;section<CodeplugWritePlan.NAMES.length;section++){
                boolean changed=false;for(int b:CodeplugWritePlan.BLOCKS[section])for(int i=0;i<before[b].length;i++)if(CodeplugWritePlan.belongs(section,b,i))changed|=before[b][i]!=after[b][i];
                if(changed)impact.append("• ").append(CodeplugWritePlan.NAMES[section]).append('\n');
            }
            new AlertDialog.Builder(this).setTitle("Удалить: "+title+"?")
                .setMessage("Запись будет удалена из проекта. Ссылки на неё будут очищены. Затронутые разделы:\n"+impact+"\nДля изменения радиостанции затем нажмите «Записать». Удаление можно отменить через меню «Проект».")
                .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{try{installProject(next);}catch(Exception e){problem(e);}}).show();
        }catch(Exception e){problem(e);}
    }

    private String channelDetails(CodeplugModel.Channel c) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.US, "[Общие настройки]\nРежим: %3$s\nПриём: %1$.6f МГц\nПередача: %2$.6f МГц\n",
                c.rxHz / 1_000_000.0, c.txHz / 1_000_000.0, c.digital ? "Цифровой (DMR)" : "Аналоговый (FM)"));
        b.append("Мощность: ").append(c.powerText()).append('\n');
        b.append("Ограничение передачи: ").append(c.totSeconds == 0 ? "выключен" : c.totSeconds + " с").append('\n');
        b.append("Шаг частоты: ").append(c.stepText()).append('\n');
        b.append("Только приём: ").append(yesNo(c.rxOnly)).append('\n');
        b.append("VOX: ").append(yesNo(c.vox)).append('\n');
        b.append("Бипер: ").append(c.beepEnabled ? "включён" : "выключен").append('\n');
        b.append("Экономайзер: ").append(c.ecoEnabled ? "включён" : "выключен").append('\n');
        b.append("Пропуск при сканировании зоны: ").append(yesNo(c.zoneSkip)).append('\n');
        b.append("Пропуск при сканировании всех каналов: ").append(yesNo(c.allSkip)).append('\n');
        b.append("Быстрый вызов: ").append(yesNo(c.fastCall)).append('\n');
        b.append("Приоритетное сканирование: ").append(yesNo(c.priority)).append('\n');

        b.append("\n[Геопозиционирование]\n");
        if (c.useLocation) {
            b.append(String.format(Locale.US, "Использовать координаты: да\nШирота/долгота: %.4f / %.4f\n", c.latitude, c.longitude));
        } else {
            b.append("Использовать координаты: нет\n");
        }

        if (c.digital) {
            b.append("\n[Цифровая связь (DMR)]\nЦветовой код: ").append(c.colorCode)
                    .append("\nТаймслот: ").append(c.timeSlot)
                    .append("\nКонтакт: ").append(refContact(c.contactIndex))
                    .append("\nГруппа приёма: ").append(refRxGroup(c.rxGroupIndex))
                    .append("\nDMR ID канала: ").append(c.optionalDmrId == 0 ? "—" : c.optionalDmrId)
                    .append("\nПрямая связь (DMO): ").append(yesNo(c.forceDmo))
                    .append("\nРоуминг: ").append(yesNo(c.roaming))
                    .append("\nПередача псевдонима (TA), TS1: ").append(c.taText(c.taTxTs1))
                    .append("\nПередача псевдонима (TA), TS2: ").append(c.taText(c.taTxTs2));
        } else {
            b.append("\n[Аналоговая связь (FM)]\nСубтон приёма: ").append(c.rxTone.displayText())
                    .append("\nСубтон передачи: ").append(c.txTone.displayText())
                    .append("\nПолоса: ").append(c.wide25k ? "25 кГц" : "12.5 кГц")
                    .append("\nШумоподавитель: ").append(c.squelchText())
                    .append("\nНастройка APRS: ").append(refAprs(c.aprsConfigIndex));
        }

        return b.toString();
    }

    private String aprsDetails(CodeplugModel.AprsConfig a) {
        return "SSID: " + a.senderSsid
                + String.format(Locale.US, "\nШирота/долгота: %.4f / %.4f", a.latitude, a.longitude)
                + "\nМаршрут 1: " + emptyDash(a.via1) + "-" + a.via1Ssid
                + "\nМаршрут 2: " + emptyDash(a.via2) + "-" + a.via2Ssid
                + "\nТаблица/номер значка: " + a.iconTable + "/" + a.iconIndex
                + "\nКомментарий: " + emptyDash(a.comment)
                + String.format(Locale.US, "\nTX: %.6f МГц", a.txHz / 1_000_000.0)
                + "\nСкорость 300 бод: " + yesNo((a.flags & 0x01) != 0)
                + "\nИспользовать координаты: " + yesNo((a.flags & 0x02) != 0)
                + "\nПередавать QSY: " + yesNo((a.flags & 0x04) != 0)
                + "\nБеззвучный маяк: " + yesNo((a.flags & 0x08) != 0)
                + String.format(Locale.US, "\nflags=0x%02X magic=0x%04X", a.flags, a.magic);
    }

    private String memberChannels(List<Integer> indices) {
        if (indices.isEmpty()) return "Каналов нет";
        StringBuilder b = new StringBuilder();
        for (int idx : indices) b.append(refChannel(idx)).append('\n');
        return b.toString().trim();
    }

    private String refChannel(int index) {
        if(index==-1)return "Текущий канал";
        if (index <= 0) return "—";
        for (CodeplugModel.Channel c : model.channels) {
            if (c.index == index) return "#" + index + " " + c.name;
        }
        return "#" + index;
    }

    private String refContact(int index) {
        if (index <= 0) return "—";
        for (CodeplugModel.Contact c : model.contacts) {
            if (c.index == index) return "#" + index + " " + c.name;
        }
        return "#" + index;
    }

    private String refRxGroup(int index) {
        if (index <= 0) return "—";
        for (CodeplugModel.RxGroup g : model.rxGroups) {
            if (g.index == index) return "#" + index + " " + g.name;
        }
        return "#" + index;
    }

    private String refAprs(int index) {
        if (index <= 0) return "—";
        for (CodeplugModel.AprsConfig a : model.aprsConfigs) {
            if (a.index == index) return "#" + index + " " + a.name;
        }
        return "#" + index;
    }

    private void refreshSummary() {
        model = CodeplugSession.current;
        boolean loaded = model != null && CodeplugSession.project != null;
        refreshAddButton();
        listView.setVisibility(loaded ? View.VISIBLE : View.GONE);
        navigationButton.setEnabled(loaded);
        findViewById(R.id.searchToggleButton).setVisibility(loaded?View.VISIBLE:View.GONE);
        if(!loaded)findViewById(R.id.projectSearchRow).setVisibility(View.GONE);
        findViewById(R.id.editGeneralButton).setVisibility(loaded && (activeCategory == 13 || activeCategory == 10 || activeCategory == 9 || activeCategory == 14 || activeCategory == 16) ? View.VISIBLE : View.GONE);
        if (!loaded) {
            summaryText.setMaxLines(4);
            summaryText.setText("Откройте проект через меню ⋮ или прочитайте радиостанцию с главного экрана.");
            return;
        }
        summaryText.setMaxLines(1);
        summaryText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        String name = model.general.radioName.isEmpty() ? "Проект" : model.general.radioName;
        summaryText.setText(name + " • " + (CodeplugSession.project.changedBytes()>0?"есть правки":"без правок"));
    }

    private void commitEdit(CodeplugProject.Change change) throws Exception {
        installProject(CodeplugSession.project.edit(change));
    }

    private void installProject(CodeplugProject next) throws Exception {
        CodeplugProjectStore.save(this, next);
        CodeplugSession.install(next);
        refreshSummary();
        if(searching)searchProject();else showCategory(activeCategory);
    }

    private void problem(Exception e) {
        new AlertDialog.Builder(this).setTitle("Операция не выполнена").setMessage(e.getMessage())
                .setPositiveButton("OK", null).show();
    }

    private void projectMenu() {
        String[] items = {"Открыть файл проекта", "Сохранить копию проекта в файл",
                "Отменить последнее изменение", "Вернуть исходное чтение", "Список изменений проекта", "Сравнить с другим проектом", "Проверить проект", "История версий", "Восстановить из резервной копии"};
        new AlertDialog.Builder(this).setTitle("Проект (.ogcproj)").setItems(items, (dialog, which) -> {
            try {
                if (which == 0) {
                    if (CodeplugSession.project != null && CodeplugSession.project.changedBytes() > 0) {
                        new AlertDialog.Builder(this).setTitle("Открыть другой проект?")
                                .setMessage("Текущий рабочий проект будет заменён. Сохраните его копию в файл, если хотите к нему вернуться.")
                                .setNegativeButton("Отмена", null).setPositiveButton("Открыть", (d, w) -> openProject()).show();
                    } else openProject();
                    return;
                }
                if(which==7){showHistory();return;}
                CodeplugProject project = CodeplugSession.project;
                if (project == null) throw new IllegalArgumentException("Сначала откройте проект или прочитайте радиостанцию");
                if (which == 1) {
                    pendingExport = project.encode();
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_TITLE, "MD9600-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                            .format(new java.util.Date()) + ".ogcproj");
                    startActivityForResult(intent, SAVE_PROJECT);
                } else if (which == 2) {
                    if (!project.canUndo()) throw new IllegalArgumentException("Нет шагов для отмены в этой сессии. Исходное чтение сохранено в проекте.");
                    installProject(project.undo());
                } else if (which == 3) {
                    new AlertDialog.Builder(this).setTitle("Вернуть исходное чтение?")
                            .setMessage("Правки в рабочей копии будут отменены. Радиостанция не изменяется.")
                            .setNegativeButton("Отмена", null).setPositiveButton("Вернуть", (d,w) -> {
                                try { installProject(project.reset()); } catch (Exception e) { problem(e); }
                            }).show();
                } else if(which==4){
                    showProjectChanges();
                } else if(which==5){
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),COMPARE_PROJECT);
                } else if(which==6){
                    ProjectCheck.Report check=ProjectCheck.inspect(project,project.working);
                    new AlertDialog.Builder(this).setTitle("Проверка проекта").setMessage(check.text()).setPositiveButton("Закрыть",null).show();
                } else if(which==7){
                    showHistory();
                } else if(which==8){
                    backupMenu();

                }
            } catch (Exception e) { problem(e); }
        }).show();
    }

    private void showProjectChanges(){
        CodeplugProject p=CodeplugSession.project;
        ProjectReportDialogs.show(this,"Изменения проекта","Исходное чтение → рабочая копия",new ProjectDiff(p.original,p.working),null);
    }
    private void compareProject(CodeplugProject other,String label){
        CodeplugProject current=CodeplugSession.project;
        if(current==null){problem(new IllegalStateException("Сначала откройте текущий проект"));return;}
        String identity=ProjectRecovery.sameRadio(current.identity,other.identity)?"Модель и прошивка совпадают":"Модель или прошивка отличаются";
        ProjectReportDialogs.show(this,"Сравнение проектов","Текущий проект → "+label+"\n"+identity,new ProjectDiff(current.working,other.working),null);
    }
    private void showHistory(){
        java.io.File[] versions=CodeplugProjectStore.versions(this);
        if(versions.length==0&&CodeplugSession.project==null){problem(new IllegalArgumentException("Сохранённых версий пока нет"));return;}
        if(versions.length==0){try{CodeplugProjectStore.save(this,CodeplugSession.project);versions=CodeplugProjectStore.versions(this);}catch(Exception e){problem(e);return;}}
        final java.io.File[] files=versions;
        String[] labels=new String[files.length];
        for(int i=0;i<files.length;i++){
            String stamp=files[i].getName().substring(0,13);
            labels[i]=new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault()).format(new java.util.Date(Long.parseLong(stamp)))+" · "+(i+1);
        }
        new AlertDialog.Builder(this).setTitle("Последние 30 сохранённых версий").setItems(labels,(dialog,which)->{
            try(java.io.InputStream in=new java.io.FileInputStream(files[which])){
                CodeplugProject revision=CodeplugProject.read(in);
                new AlertDialog.Builder(this).setTitle(labels[which]+" · "+revision.model().general.radioName)
                    .setItems(new String[]{"Сравнить с текущим проектом","Открыть эту версию","Экспортировать версию"},(d,action)->{
                        try{
                            if(action==0)compareProject(revision,labels[which]);
                            else if(action==1){
                                CodeplugProject base=CodeplugSession.project;
                                ProjectReportDialogs.show(this,"Открыть сохранённую версию", "Рабочий проект будет заменён. Перед записью проверяется совпадение рации с исходным чтением этой версии.",new ProjectDiff(base==null?revision.original:base.working,revision.working),()->{
                                    try{if(CodeplugSession.project!=base)throw new IllegalStateException("Проект изменился");installProject(revision);}catch(Exception e){problem(e);}
                                });
                            }else{
                                pendingExport=revision.encode();startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,files[which].getName()),SAVE_PROJECT);
                            }
                        }catch(Exception e){problem(e);}
                    }).show();
            }catch(Exception e){problem(e);}
        }).setNegativeButton("Закрыть",null).show();
    }
    private void backupMenu(){
        new AlertDialog.Builder(this).setTitle("Восстановление codeplug").setItems(new String[]{"Выбрать копию на телефоне","Открыть ZIP-файл резервной копии"},(dialog,which)->{
            if(which==1){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),RESTORE_BACKUP);return;}
            java.io.File[] files=new java.io.File(getFilesDir(),"codeplug-backups").listFiles((dir,name)->name.endsWith(".zip"));
            if(files==null||files.length==0){problem(new IllegalArgumentException("Резервных копий записи codeplug пока нет"));return;}
            java.util.Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));String[] names=new String[files.length];for(int i=0;i<files.length;i++)names[i]=files[i].getName();
            new AlertDialog.Builder(this).setTitle("Копии записи codeplug").setItems(names,(d,index)->reviewOperation("Восстановление из "+names[index],base->{
                try(java.io.InputStream in=new java.io.FileInputStream(files[index])){return ProjectRecovery.read(in).restore(base);}
            })).setNegativeButton("Отмена",null).show();
        }).setNegativeButton("Отмена",null).show();
    }

    private void openProject() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, OPEN_PROJECT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) { pendingExport=null; return; }
        try {
            if(requestCode==COMPARE_PROJECT){
                try(java.io.InputStream in=getContentResolver().openInputStream(data.getData())){compareProject(CodeplugProject.read(in),"выбранный файл");}
            }else if(requestCode==RESTORE_BACKUP){
                final android.net.Uri uri=data.getData();
                reviewOperation("Восстановление из резервной копии",base->{
                    try(java.io.InputStream in=getContentResolver().openInputStream(uri)){return ProjectRecovery.read(in).restore(base);}
                });
            }else if (requestCode == OPEN_PROJECT) {
                try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                    installProject(CodeplugProject.read(in));
                }
            } else if(requestCode==IMPORT_CHANNELS){
                final android.net.Uri uri=data.getData();
                reviewOperation("Импорт каналов CSV",base->{
                    java.util.List<java.util.Map<String,String>> rows;
                    try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
                        if(in==null)throw new java.io.IOException("Файл не открыт");
                        java.nio.charset.CharsetDecoder decoder=java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
                        rows=ChannelCsv.read(new java.io.InputStreamReader(in,decoder));
                    }return base.edit(snapshot->ChannelCsv.append(snapshot,rows));
                });
            } else if(requestCode==EXPORT_CHANNELS){
                byte[] bytes=pendingExport!=null?pendingExport:ChannelCsv.export(model).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){
                    if(out==null)throw new java.io.IOException("Файл не открыт");out.write(bytes);out.flush();
                }android.widget.Toast.makeText(this,"Таблица каналов сохранена",android.widget.Toast.LENGTH_LONG).show();
            } else if (requestCode == SAVE_PROJECT) {
                byte[] bytes = pendingExport != null ? pendingExport : CodeplugSession.project.encode();
                try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (out == null) throw new java.io.IOException("Не удалось открыть файл для записи");
                    out.write(bytes); out.flush();
                }
                android.widget.Toast.makeText(this, "Копия проекта сохранена", android.widget.Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) { problem(e); }
        finally { pendingExport=null; }
    }

    private static String available(String s) {
        return s == null || s.trim().isEmpty() ? "не получено по USB" : s;
    }
    private static String stored(String s) {
        return s == null || s.trim().isEmpty() ? "не заполнено в codeplug" : s;
    }

    private static String yesNo(boolean v) { return v ? "да" : "нет"; }
    private static String emptyDash(String s) { return s == null || s.isEmpty() ? "—" : s; }
}
