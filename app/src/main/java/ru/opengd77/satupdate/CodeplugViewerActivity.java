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
    private Spinner categorySpinner;
    private ListView listView;
    private TextView summaryText;
    private final List<Object> visibleObjects = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_codeplug_viewer);

        model = CodeplugSession.current;
        summaryText = findViewById(R.id.codeplugSummaryText);
        categorySpinner = findViewById(R.id.codeplugCategorySpinner);
        listView = findViewById(R.id.codeplugList);
        findViewById(R.id.codeplugCloseButton).setOnClickListener(v -> returnToMain());

        if (model == null) {
            summaryText.setText("Codeplug не загружен. Вернитесь назад и выполните чтение радиостанции.");
            listView.setVisibility(View.GONE);
            categorySpinner.setVisibility(View.GONE);
            return;
        }

        String name = model.general.radioName.isEmpty() ? "без имени" : model.general.radioName;
        summaryText.setText(name + " • DMR ID " + model.general.dmrId + "\n"
                + model.compactSummary() + "\n"
                + "Прочитано: " + model.rawBytes + " байт • просмотр codeplug • только чтение");

        String[] categories = {"Обзор", "Каналы", "VFO A/B", "Зоны", "DMR контакты",
                "Группы приёма", "Списки сканирования", "APRS", "DTMF контакты", "DTMF настройки",
                "Заставка", "Сведения о станции", "Спутники"};
        categorySpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, categories));
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showCategory(position);
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

    private void showCategory(int category) {
        visibleObjects.clear();
        List<String> rows = new ArrayList<>();
        switch (category) {
            case 0:
                addText(rows, "Имя станции: " + emptyDash(model.general.radioName));
                addText(rows, "DMR ID: " + model.general.dmrId);
                addText(rows, "Версия codeplug: " + model.general.codeplugVersion);
                addText(rows, "Чувствительность VOX: " + model.general.voxSense);
                addText(rows, String.format(Locale.US, "Общие флаги (HEX): %02X %02X %02X %02X",
                        model.general.flag1, model.general.flag2, model.general.flag3, model.general.flag4));
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
        addText(rows, "Версия CPS из codeplug: " + stored(d.cpsVersion));
        addText(rows, "Аппаратная версия (HW): " + stored(d.hardwareVersion));
        addText(rows, "Поле FW из codeplug: " + stored(d.firmwareVersion));
        addText(rows, "Версия DSP из codeplug: " + stored(d.dspVersion));
        addText(rows, "Границы UHF: " + d.uhfRangeText());
        addText(rows, "Границы VHF: " + d.vhfRangeText());
        addText(rows, "Модель и строка идентификации FW получены по USB. Эта строка может не содержать номер версии. Остальные поля — из codeplug; "
                + "они могут быть пустыми или относиться к прежней прошивке. "
                + "Границы из codeplug не обозначают текущие ограничения передачи.");
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

        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    private String channelDetails(CodeplugModel.Channel c) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.US, "RX: %.6f МГц\nTX: %.6f МГц\nРежим: %s\n",
                c.rxHz / 1_000_000.0, c.txHz / 1_000_000.0, c.digital ? "DMR" : "FM"));
        b.append("Мощность: ").append(c.powerText()).append('\n');
        b.append("TOT: ").append(c.totSeconds == 0 ? "выключен" : c.totSeconds + " с").append('\n');
        b.append("Шаг частоты: ").append(c.stepText()).append('\n');
        b.append("Только приём: ").append(yesNo(c.rxOnly)).append('\n');
        b.append("VOX: ").append(yesNo(c.vox)).append('\n');
        b.append("Бипер: ").append(c.beepEnabled ? "включён" : "выключен").append('\n');
        b.append("Экономайзер: ").append(c.ecoEnabled ? "включён" : "выключен").append('\n');
        b.append("Пропуск при сканировании зоны: ").append(yesNo(c.zoneSkip)).append('\n');
        b.append("Пропуск при сканировании всех каналов: ").append(yesNo(c.allSkip)).append('\n');
        b.append("Быстрый вызов (RUS): ").append(yesNo(c.fastCall)).append('\n');
        b.append("Приоритет (RUS): ").append(yesNo(c.priority)).append('\n');

        if (c.useLocation) {
            b.append(String.format(Locale.US, "Использовать координаты: да\nШирота/долгота: %.4f / %.4f\n", c.latitude, c.longitude));
        } else {
            b.append("Использовать координаты: нет\n");
        }

        if (c.digital) {
            b.append("\n[DMR]\nЦветовой код: ").append(c.colorCode)
                    .append("\nТаймслот: ").append(c.timeSlot)
                    .append("\nКонтакт: ").append(refContact(c.contactIndex))
                    .append("\nГруппа приёма: ").append(refRxGroup(c.rxGroupIndex))
                    .append("\nDMR ID канала: ").append(c.optionalDmrId == 0 ? "—" : c.optionalDmrId)
                    .append("\nПринудительный DMO: ").append(yesNo(c.forceDmo))
                    .append("\nРоуминг: ").append(yesNo(c.roaming))
                    .append("\nПередача псевдонима (TA), TS1: ").append(c.taText(c.taTxTs1))
                    .append("\nПередача псевдонима (TA), TS2: ").append(c.taText(c.taTxTs2));
        } else {
            b.append("\n[FM]\nСубтон приёма: ").append(c.rxTone.displayText())
                    .append("\nСубтон передачи: ").append(c.txTone.displayText())
                    .append("\nПолоса: ").append(c.wide25k ? "25 кГц" : "12.5 кГц")
                    .append("\nШумоподавитель: ").append(c.squelchText())
                    .append("\nНастройка APRS: ").append(refAprs(c.aprsConfigIndex));
        }

        b.append("\n\n[Служебные поля / исходные значения]\nРазрешён прямой канал (Talkaround): ").append(yesNo(c.allowTalkaround))
                .append("\nSTE: ").append(c.ste)
                .append(" • NonSTE: ").append(c.nonSte)
                .append(" • DataPL: ").append(yesNo(c.dataPl))
                .append("\nТип PTT ID: ").append(c.pttidType)
                .append(" • Двойная ёмкость: ").append(yesNo(c.dualCapacity))
                .append("\nПредпочтение синхронизации: ").append(c.timingPreference)
                .append(" • ARS: ").append(c.ars)
                .append(" • Переключатель ключа: ").append(c.keySwitch)
                .append("\nЗаголовок данных UDP: ").append(yesNo(c.udpDataHead))
                .append(" • Разрешено прерывание передачи: ").append(yesNo(c.allowTxInterrupt))
                .append("\nЧастота прерывания передачи: ").append(yesNo(c.txInterruptFreq))
                .append(" • Индивидуальный вызов: ").append(yesNo(c.privateCall))
                .append(String.format(Locale.US,
                        "\nRUS=%02X Libre=%02X flag1=%02X flag2=%02X flag3=%02X flag4=%02X",
                        c.rawOpenGd77Rus, c.rawLibreFlags, c.rawFlag1, c.rawFlag2,
                        c.rawFlag3, c.rawFlag4));
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

    private static String available(String s) {
        return s == null || s.trim().isEmpty() ? "не получено по USB" : s;
    }
    private static String stored(String s) {
        return s == null || s.trim().isEmpty() ? "не заполнено в codeplug" : s;
    }

    private static String yesNo(boolean v) { return v ? "да" : "нет"; }
    private static String emptyDash(String s) { return s == null || s.isEmpty() ? "—" : s; }
}
