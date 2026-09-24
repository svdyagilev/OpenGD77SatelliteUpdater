package ru.opengd77.satupdate;

import android.app.Activity;
import android.app.AlertDialog;
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
        findViewById(R.id.codeplugCloseButton).setOnClickListener(v -> finish());

        if (model == null) {
            summaryText.setText("Codeplug не загружен. Вернитесь назад и выполните чтение радиостанции.");
            listView.setVisibility(View.GONE);
            categorySpinner.setVisibility(View.GONE);
            return;
        }

        String name = model.general.radioName.isEmpty() ? "без имени" : model.general.radioName;
        summaryText.setText(name + " • DMR ID " + model.general.dmrId + "\n"
                + model.compactSummary() + "\n"
                + "Прочитано raw: " + model.rawBytes + " bytes • режим только чтение");

        String[] categories = {"Обзор", "Каналы", "Зоны", "DMR контакты", "RX Groups", "Scan Lists"};
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

    private void showCategory(int category) {
        visibleObjects.clear();
        List<String> rows = new ArrayList<>();
        switch (category) {
            case 0:
                rows.add("Radio name: " + (model.general.radioName.isEmpty() ? "—" : model.general.radioName));
                rows.add("DMR ID: " + model.general.dmrId);
                rows.add("Channels: " + model.channels.size() + " / 1024");
                rows.add("Zones: " + model.zones.size() + " / 250");
                rows.add("DMR Contacts: " + model.contacts.size() + " / 1024");
                rows.add("RX Groups: " + model.rxGroups.size() + " / 76");
                rows.add("Scan Lists: " + model.scanLists.size() + " / 64");
                for (String row : rows) visibleObjects.add(row);
                break;
            case 1:
                for (CodeplugModel.Channel c : model.channels) {
                    visibleObjects.add(c);
                    rows.add(c.oneLine());
                }
                break;
            case 2:
                for (CodeplugModel.Zone z : model.zones) {
                    visibleObjects.add(z);
                    rows.add(z.oneLine());
                }
                break;
            case 3:
                for (CodeplugModel.Contact c : model.contacts) {
                    visibleObjects.add(c);
                    rows.add(c.oneLine());
                }
                break;
            case 4:
                for (CodeplugModel.RxGroup g : model.rxGroups) {
                    visibleObjects.add(g);
                    rows.add(g.oneLine());
                }
                break;
            case 5:
                for (CodeplugModel.ScanList s : model.scanLists) {
                    visibleObjects.add(s);
                    rows.add(s.oneLine());
                }
                break;
        }
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
    }

    private void showDetails(Object obj) {
        if (obj instanceof String) return;
        String title;
        String message;
        if (obj instanceof CodeplugModel.Channel) {
            CodeplugModel.Channel c = (CodeplugModel.Channel)obj;
            title = "Channel " + c.index + " • " + c.name;
            message = String.format(Locale.US,
                    "RX: %.6f MHz\nTX: %.6f MHz\nMode: %s\n",
                    c.rxHz / 1_000_000.0, c.txHz / 1_000_000.0, c.digital ? "DMR" : "FM");
            if (c.digital) {
                message += "Color Code: " + c.colorCode + "\nTime Slot: " + c.timeSlot
                        + "\nContact: " + refContact(c.contactIndex)
                        + "\nRX Group: " + refRxGroup(c.rxGroupIndex);
            } else {
                message += "Bandwidth: " + (c.wide25k ? "25 kHz" : "12.5 kHz")
                        + "\nRX only: " + (c.rxOnly ? "да" : "нет");
            }
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
        } else {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
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
}
