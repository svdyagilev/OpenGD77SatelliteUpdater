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
                + "Прочитано raw: " + model.rawBytes + " bytes • v0.5 deep audit • только чтение");

        String[] categories = {"Обзор", "Каналы", "VFO A/B", "Зоны", "DMR контакты",
                "RX Groups", "Scan Lists", "APRS", "DTMF контакты", "DTMF настройки",
                "Boot / Quick Keys", "Device Info", "Satellites"};
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
                addText(rows, "Radio name: " + emptyDash(model.general.radioName));
                addText(rows, "DMR ID: " + model.general.dmrId);
                addText(rows, "Codeplug version: " + model.general.codeplugVersion);
                addText(rows, "VOX sense: " + model.general.voxSense);
                addText(rows, String.format(Locale.US, "General flags: %02X %02X %02X %02X",
                        model.general.flag1, model.general.flag2, model.general.flag3, model.general.flag4));
                addText(rows, "Channels: " + model.channels.size() + " / 1024");
                addText(rows, "Zones: " + model.zones.size() + " / 250");
                addText(rows, "DMR Contacts: " + model.contacts.size() + " / 1024");
                addText(rows, "RX Groups: " + model.rxGroups.size() + " / 76");
                addText(rows, "Scan Lists: " + model.scanLists.size() + " / 64");
                addText(rows, "APRS configs: " + model.aprsConfigs.size() + " / 8");
                addText(rows, "DTMF Contacts: " + model.dtmfContacts.size() + " / 63");
                addText(rows, "Satellites: " + model.satellites.size() + " / 25");
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
                if (rows.isEmpty()) addText(rows, "Satellite TLV не найден или пуст");
                break;
        }
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
    }

    private void showDtmfSettings(List<String> rows) {
        CodeplugModel.DtmfSettings d = model.dtmfSettings;
        addText(rows, "Self ID: " + emptyDash(d.selfId));
        addText(rows, "Kill code: " + emptyDash(d.killCode));
        addText(rows, "Wake code: " + emptyDash(d.wakeCode));
        addText(rows, "Delimiter raw: " + d.delimiter + " • Group raw: " + d.groupCode);
        addText(rows, "Decode response: " + d.decodeResponse + " • Auto reset: " + d.autoResetSeconds + " s");
        addText(rows, "Kill/Wake decode: " + yesNo(d.killWakeDecode) + " • Kill type: " + d.killType);
        addText(rows, "PTT ID up: " + emptyDash(d.pttUp));
        addText(rows, "PTT ID down: " + emptyDash(d.pttDown));
        addText(rows, String.format(Locale.US, "Response hold: %.1f s • Decode: %.1f s",
                d.responseHoldSeconds, d.decodeTimeSeconds));
        addText(rows, "First digit delay: " + d.firstDigitDelayMs + " ms");
        addText(rows, "First/other duration: " + d.firstDigitDurationMs + " / " + d.otherDurationMs + " ms");
        addText(rows, "Rate raw: " + d.rate + " • Tail: " + d.tailMs + " ms");
    }

    private void showBoot(List<String> rows) {
        CodeplugModel.BootInfo b = model.boot;
        addText(rows, "Intro mode: " + (b.introMode == 1 ? "Text" : b.introMode == 0 ? "Picture" : "RAW " + b.introMode));
        addText(rows, "Boot password: " + (b.passwordEnabled ? "enabled" : "disabled") + " (PIN не показывается)");
        addText(rows, "Line 1: " + emptyDash(b.line1));
        addText(rows, "Line 2: " + emptyDash(b.line2));
        for (int i = 0; i < b.quickKeys.size(); i++) {
            addText(rows, String.format(Locale.US, "Quick key %d: 0x%04X", i, b.quickKeys.get(i)));
        }
    }

    private void showDeviceInfo(List<String> rows) {
        CodeplugModel.DeviceInfo d = model.deviceInfo;
        addText(rows, "Model: " + emptyDash(d.model));
        addText(rows, "Serial: " + emptyDash(d.serial));
        addText(rows, "CPS version: " + emptyDash(d.cpsVersion));
        addText(rows, "Hardware: " + emptyDash(d.hardwareVersion));
        addText(rows, "Firmware field: " + emptyDash(d.firmwareVersion));
        addText(rows, "DSP: " + emptyDash(d.dspVersion));
        addText(rows, "Band limits raw UHF: " + d.minUhf + " .. " + d.maxUhf);
        addText(rows, "Band limits raw VHF: " + d.minVhf + " .. " + d.maxVhf);
    }

    private void showDetails(Object obj) {
        if (obj instanceof String) return;
        String title;
        String message;
        if (obj instanceof CodeplugModel.Channel) {
            CodeplugModel.Channel c = (CodeplugModel.Channel)obj;
            title = (c.index > 0 ? "Channel " + c.index : "VFO") + " • " + c.name;
            message = channelDetails(c);
        } else if (obj instanceof CodeplugModel.Zone) {
            CodeplugModel.Zone z = (CodeplugModel.Zone)obj;
            title = "Zone " + z.index + " • " + z.name;
            message = memberChannels(z.channelIndices);
        } else if (obj instanceof CodeplugModel.Contact) {
            CodeplugModel.Contact c = (CodeplugModel.Contact)obj;
            title = "Contact " + c.index + " • " + c.name;
            message = "Type: " + c.typeText() + "\nID/TG: " + c.number
                    + "\nTS override: " + (c.tsOverride == 0x00 ? "TS1" : c.tsOverride == 0x02 ? "TS2" : "нет");
        } else if (obj instanceof CodeplugModel.RxGroup) {
            CodeplugModel.RxGroup g = (CodeplugModel.RxGroup)obj;
            title = "RX Group " + g.index + " • " + g.name;
            StringBuilder b = new StringBuilder();
            for (int idx : g.contactIndices) b.append(refContact(idx)).append('\n');
            message = b.length() == 0 ? "Контактов нет" : b.toString().trim();
        } else if (obj instanceof CodeplugModel.ScanList) {
            CodeplugModel.ScanList s = (CodeplugModel.ScanList)obj;
            title = "Scan List " + s.index + " • " + s.name;
            message = memberChannels(s.channelIndices)
                    + "\n\nPriority 1: " + refChannel(s.primary)
                    + "\nPriority 2: " + refChannel(s.secondary)
                    + "\nRevert: " + refChannel(s.revert);
        } else if (obj instanceof CodeplugModel.AprsConfig) {
            CodeplugModel.AprsConfig a = (CodeplugModel.AprsConfig)obj;
            title = "APRS " + a.index + " • " + a.name;
            message = aprsDetails(a);
        } else if (obj instanceof CodeplugModel.DtmfContact) {
            CodeplugModel.DtmfContact d = (CodeplugModel.DtmfContact)obj;
            title = "DTMF " + d.index + " • " + d.name;
            message = "Code: " + emptyDash(d.code);
        } else if (obj instanceof CodeplugModel.Satellite) {
            CodeplugModel.Satellite s = (CodeplugModel.Satellite)obj;
            title = "Satellite • " + s.name;
            message = Double.isNaN(s.ageDays) ? "Epoch: неизвестен"
                    : String.format(Locale.US, "Возраст Keps: %.2f days", s.ageDays);
        } else {
            return;
        }

        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    private String channelDetails(CodeplugModel.Channel c) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.US, "RX: %.6f MHz\nTX: %.6f MHz\nMode: %s\n",
                c.rxHz / 1_000_000.0, c.txHz / 1_000_000.0, c.digital ? "DMR" : "FM"));
        b.append("Мощность: ").append(c.powerText()).append('\n');
        b.append("TOT: ").append(c.totSeconds == 0 ? "∞ / off" : c.totSeconds + " s").append('\n');
        b.append("Step: ").append(c.stepText()).append('\n');
        b.append("RX only: ").append(yesNo(c.rxOnly)).append('\n');
        b.append("VOX: ").append(yesNo(c.vox)).append('\n');
        b.append("Бипер: ").append(c.beepEnabled ? "включён" : "выключен").append('\n');
        b.append("Экономайзер: ").append(c.ecoEnabled ? "включён" : "выключен").append('\n');
        b.append("Zone skip: ").append(yesNo(c.zoneSkip)).append('\n');
        b.append("All skip: ").append(yesNo(c.allSkip)).append('\n');
        b.append("Fastcall (RUS): ").append(yesNo(c.fastCall)).append('\n');
        b.append("Priority (RUS): ").append(yesNo(c.priority)).append('\n');

        if (c.useLocation) {
            b.append(String.format(Locale.US, "Use location: да\nLat/Lon: %.4f / %.4f\n", c.latitude, c.longitude));
        } else {
            b.append("Use location: нет\n");
        }

        if (c.digital) {
            b.append("\n[DMR]\nColor Code: ").append(c.colorCode)
                    .append("\nTime Slot: ").append(c.timeSlot)
                    .append("\nContact: ").append(refContact(c.contactIndex))
                    .append("\nRX Group: ").append(refRxGroup(c.rxGroupIndex))
                    .append("\nOptional DMR ID: ").append(c.optionalDmrId == 0 ? "—" : c.optionalDmrId)
                    .append("\nForce DMO: ").append(yesNo(c.forceDmo))
                    .append("\nRoaming: ").append(yesNo(c.roaming))
                    .append("\nTA TX TS1: ").append(c.taText(c.taTxTs1))
                    .append("\nTA TX TS2: ").append(c.taText(c.taTxTs2));
        } else {
            b.append("\n[FM]\nRX subtone: ").append(c.rxTone.displayText())
                    .append("\nTX subtone: ").append(c.txTone.displayText())
                    .append("\nBandwidth: ").append(c.wide25k ? "25 kHz" : "12.5 kHz")
                    .append("\nSquelch: ").append(c.squelchText())
                    .append("\nAPRS config: ").append(refAprs(c.aprsConfigIndex));
        }

        b.append("\n\n[Legacy / RAW]\nAllow Talkaround: ").append(yesNo(c.allowTalkaround))
                .append("\nSTE: ").append(c.ste)
                .append(" • NonSTE: ").append(c.nonSte)
                .append(" • DataPL: ").append(yesNo(c.dataPl))
                .append("\nPTT ID type: ").append(c.pttidType)
                .append(" • Dual capacity: ").append(yesNo(c.dualCapacity))
                .append("\nTiming pref: ").append(c.timingPreference)
                .append(" • ARS: ").append(c.ars)
                .append(" • KeySwitch: ").append(c.keySwitch)
                .append("\nUDP data head: ").append(yesNo(c.udpDataHead))
                .append(" • Allow TX interrupt: ").append(yesNo(c.allowTxInterrupt))
                .append("\nTX interrupt freq: ").append(yesNo(c.txInterruptFreq))
                .append(" • Private call: ").append(yesNo(c.privateCall))
                .append(String.format(Locale.US,
                        "\nRUS=%02X Libre=%02X flag1=%02X flag2=%02X flag3=%02X flag4=%02X",
                        c.rawOpenGd77Rus, c.rawLibreFlags, c.rawFlag1, c.rawFlag2,
                        c.rawFlag3, c.rawFlag4));
        return b.toString();
    }

    private String aprsDetails(CodeplugModel.AprsConfig a) {
        return "SSID: " + a.senderSsid
                + String.format(Locale.US, "\nLat/Lon: %.4f / %.4f", a.latitude, a.longitude)
                + "\nVia 1: " + emptyDash(a.via1) + "-" + a.via1Ssid
                + "\nVia 2: " + emptyDash(a.via2) + "-" + a.via2Ssid
                + "\nIcon table/index: " + a.iconTable + "/" + a.iconIndex
                + "\nComment: " + emptyDash(a.comment)
                + String.format(Locale.US, "\nTX: %.6f MHz", a.txHz / 1_000_000.0)
                + "\n300 baud flag: " + yesNo((a.flags & 0x01) != 0)
                + "\nUse position: " + yesNo((a.flags & 0x02) != 0)
                + "\nTransmit QSY: " + yesNo((a.flags & 0x04) != 0)
                + "\nSilent beacon: " + yesNo((a.flags & 0x08) != 0)
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

    private static String yesNo(boolean v) { return v ? "да" : "нет"; }
    private static String emptyDash(String s) { return s == null || s.isEmpty() ? "—" : s; }
}
