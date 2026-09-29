package ru.opengd77.satupdate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CodeplugViewerActivity extends Activity {
    private CodeplugModel model;
    private int activeCategory;
    private static final int OPEN_PROJECT = 601, SAVE_PROJECT = 602;
    private byte[] pendingExport;
    private Spinner categorySpinner;
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
        categorySpinner = findViewById(R.id.codeplugCategorySpinner);
        listView = findViewById(R.id.codeplugList);
        findViewById(R.id.codeplugCloseButton).setOnClickListener(v -> returnToMain());

        findViewById(R.id.projectMenuButton).setOnClickListener(v -> projectMenu());
        findViewById(R.id.writeCodeplugButton).setOnClickListener(v -> {
            startActivity(new Intent(this, CodeplugWriteActivity.class));
            finish();
        });
        findViewById(R.id.editGeneralButton).setOnClickListener(v -> {
            if (model == null) return;
            if (activeCategory == 10) CodeplugEditDialogs.boot(this, model.boot, this::commitEdit);
            else CodeplugEditDialogs.general(this, model.general, this::commitEdit);
        });
        findViewById(R.id.addRecordButton).setOnClickListener(v -> {
            try { if (creationKind()!=null && CodeplugSession.project!=null)
                CodeplugEditDialogs.create(this,CodeplugSession.project,creationKind(),this::commitEdit);
            } catch(Exception e) { problem(e); }
        });
        refreshSummary();

        String[] categories = {"Обзор", "Загрузочный экран", "DMR ID и позывной",
                "Ограничения частот", "Настройки DTMF", "Настройки APRS",
                "Контакты DMR", "Контакты DTMF", "Списки групп", "Зоны", "Каналы",
                "VFO A/B", "Настройки рации", "Списки сканирования",
                "Сведения о станции", "Спутники"};
        final int[] categoryIds = {0, 10, 13, 14, 9, 7, 4, 8, 5, 3, 1, 2, 15, 6, 11, 12};
        categorySpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, categories));
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showCategory(categoryIds[position]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        listView.setOnItemClickListener((parent, view, position, id) -> showDetails(visibleObjects.get(position)));
    }

    @Override public void onBackPressed() { returnToMain(); }

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
            default:return null;
        }
    }
    private void refreshAddButton() {
        android.widget.Button button=findViewById(R.id.addRecordButton);
        CodeplugRecords.Kind kind=creationKind();
        button.setVisibility(model!=null && kind!=null?View.VISIBLE:View.GONE);
        if(kind!=null){
            button.setText(kind==CodeplugRecords.Kind.CHANNEL?"Добавить канал":kind==CodeplugRecords.Kind.ZONE?"Добавить зону":kind==CodeplugRecords.Kind.DMR?"Добавить контакт DMR":"Добавить контакт DTMF");
        }
    }
    private void showCategory(int category) {
        activeCategory = category;
        refreshAddButton();
        visibleObjects.clear();
        findViewById(R.id.editGeneralButton).setVisibility(model != null && (category == 13 || category == 10) ? View.VISIBLE : View.GONE);
        if (model == null) return;
        List<String> rows = new ArrayList<>();
        switch (category) {
            case 0:
                addText(rows, "Каналы: " + model.channels.size() + " / 1024");
                addText(rows, "Зоны: " + model.zones.size() + " / 250");
                addText(rows, "DMR контакты: " + model.contacts.size() + " / 1024");
                addText(rows, "Группы приёма: " + model.rxGroups.size() + " / 76");
                addText(rows, "Списки сканирования: " + model.scanLists.size() + " / 64");
                addText(rows, "Настройки APRS: " + model.aprsConfigs.size() + " / 8");
                addText(rows, "DTMF контакты: " + model.dtmfContacts.size() + " / 63");
                addText(rows, "Спутники: " + model.satellites.size() + " / 25");
                break;
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
            case 15:
                addText(rows, "Чувствительность VOX: " + model.general.voxSense);
                addText(rows, String.format(Locale.US, "Общие флаги (HEX): %02X %02X %02X %02X",
                        model.general.flag1, model.general.flag2, model.general.flag3, model.general.flag4));
                break;
        }
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
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
            dialog.setNeutralButton("Изменить", (d, w) ->
                    CodeplugEditDialogs.edit(this, obj, model, this::commitEdit));
        }
        dialog.show();
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
        categorySpinner.setVisibility(loaded ? View.VISIBLE : View.GONE);
        findViewById(R.id.editGeneralButton).setVisibility(loaded && (activeCategory == 13 || activeCategory == 10) ? View.VISIBLE : View.GONE);
        if (!loaded) {
            summaryText.setText("Откройте файл через «Проект» или прочитайте радиостанцию с главного экрана.");
            return;
        }
        String name = model.general.radioName.isEmpty() ? "без имени" : model.general.radioName;
        summaryText.setText(name + " • DMR ID " + model.general.dmrId + "\n" + model.compactSummary()
                + "\nИзменено байтов: " + CodeplugSession.project.changedBytes()
                + " • автосохранение на телефоне\nЗапись выбранных разделов — кнопка «Записать»");
    }

    private void commitEdit(CodeplugProject.Change change) throws Exception {
        installProject(CodeplugSession.project.edit(change));
    }

    private void installProject(CodeplugProject next) throws Exception {
        CodeplugProjectStore.save(this, next);
        CodeplugSession.install(next);
        refreshSummary();
        showCategory(activeCategory);
    }

    private void problem(Exception e) {
        new AlertDialog.Builder(this).setTitle("Операция не выполнена").setMessage(e.getMessage())
                .setPositiveButton("OK", null).show();
    }

    private void projectMenu() {
        String[] items = {"Открыть файл проекта", "Сохранить копию проекта в файл",
                "Отменить последнее изменение", "Вернуть исходное чтение", "Изменения по блокам"};
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
                } else {
                    String[] names = {"Сведения о станции", "Общие настройки", "DTMF", "APRS", "Сканирование",
                            "Контакты DTMF", "Каналы 1–128", "Загрузочный экран / VFO", "Зоны", "Каналы 129–1024",
                            "Контакты DMR", "Группы приёма", "Дополнительные настройки"};
                    byte[][] before = CodeplugProject.blocks(project.original), after = CodeplugProject.blocks(project.working);
                    StringBuilder text = new StringBuilder();
                    for (int i=0; i<before.length; i++) {
                        int count=0; for (int j=0; j<before[i].length; j++) if (before[i][j]!=after[i][j]) count++;
                        if (count>0) text.append(names[i]).append(": ").append(count).append(" байт\n");
                    }
                    new AlertDialog.Builder(this).setTitle("Изменения проекта")
                            .setMessage(text.length()==0 ? "Изменений нет" : text.toString()).setPositiveButton("OK", null).show();
                }
            } catch (Exception e) { problem(e); }
        }).show();
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
            if (requestCode == OPEN_PROJECT) {
                try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                    installProject(CodeplugProject.read(in));
                }
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
