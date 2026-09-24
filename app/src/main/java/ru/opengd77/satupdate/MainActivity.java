package ru.opengd77.satupdate;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String USB_PERMISSION = "ru.opengd77.satupdate.USB_PERMISSION";
    private static final int OPEN_TLE_REQUEST = 1001;
    private static final String CELESTRAK = "https://celestrak.org/NORAD/elements/gp.php?GROUP=amateur&FORMAT=tle";
    private static final String R4UAB = "https://r4uab.ru/satonline.txt";

    private Spinner sourceSpinner;
    private EditText urlEdit;
    private TextView status;
    private Button updateButton;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private UsbManager usbManager;
    private UsbCdcSerialTransport transport;
    private OpenGd77Protocol protocol;
    private List<SatelliteConfig> configs;
    private OpenGd77SatelliteEncoder.BuildResult prepared;
    private byte[] originalAdditional;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!USB_PERMISSION.equals(intent.getAction())) return;
            UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && d != null) {
                connectAndRead(d);
            } else log("USB permission denied");
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        transport = new UsbCdcSerialTransport(usbManager);
        protocol = new OpenGd77Protocol(transport);

        sourceSpinner = findViewById(R.id.sourceSpinner);
        urlEdit = findViewById(R.id.urlEdit);
        status = findViewById(R.id.statusText);
        updateButton = findViewById(R.id.updateButton);

        String[] sources = {"CelesTrak — amateur", "R4UAB — satonline.txt", "Свой URL"};
        sourceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sources));
        urlEdit.setText(CELESTRAK);
        sourceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (pos == 0) urlEdit.setText(CELESTRAK);
                else if (pos == 1) urlEdit.setText(R4UAB);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        try (InputStream in = getAssets().open("Satellites.txt")) {
            configs = SatelliteConfigParser.parse(in);
            log("OpenGD77 Satellite Updater v0.2");
            log("Загружено конфигураций Satellites.txt: " + configs.size());
        } catch (Exception e) { log("Ошибка Satellites.txt: " + e.getMessage()); }

        findViewById(R.id.downloadButton).setOnClickListener(v -> downloadTle());
        findViewById(R.id.openFileButton).setOnClickListener(v -> openLocalTle());
        findViewById(R.id.connectButton).setOnClickListener(v -> requestConnect());
        updateButton.setOnClickListener(v -> confirmUpdate());

        IntentFilter f = new IntentFilter(USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbReceiver, f);
    }

    private void downloadTle() {
        final String url = urlEdit.getText().toString().trim();
        log("Загрузка: " + url);
        worker.execute(() -> {
            try { prepareTle(NetworkFetcher.get(url)); }
            catch (Exception e) { log("Ошибка загрузки: " + e.getMessage()); }
        });
    }

    private void openLocalTle() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("text/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, OPEN_TLE_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OPEN_TLE_REQUEST || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        worker.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                prepareTle(new String(out.toByteArray(), StandardCharsets.US_ASCII));
            } catch (Exception e) { log("Ошибка файла TLE: " + e.getMessage()); }
        });
    }

    private void prepareTle(String text) {
        try {
            Map<Integer, TleEntry> all = TleParser.parse(text);
            prepared = OpenGd77SatelliteEncoder.buildPayload(configs, all);
            StringBuilder b = new StringBuilder();
            b.append("TLE записей в источнике: ").append(all.size()).append('\n');
            b.append("Будут загружены: ").append(prepared.loaded.size()).append('\n');
            for (String s : prepared.loaded) b.append("  + ").append(s).append('\n');
            if (!prepared.missing.isEmpty()) {
                b.append("Не найдены: ").append(prepared.missing.size()).append('\n');
                for (String s : prepared.missing) b.append("  - ").append(s).append('\n');
            }
            log(b.toString());
            updateWriteButton();
        } catch (Exception e) { log("Ошибка разбора TLE: " + e.getMessage()); }
    }

    private void requestConnect() {
        UsbDevice d = transport.findDevice();
        if (d == null) { log("MD-9600/OpenGD77 USB 1FC9:0094 не найден"); return; }
        log(transport.describeDevice(d));
        if (usbManager.hasPermission(d)) connectAndRead(d);
        else {
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(d, pi);
        }
    }

    private void connectAndRead(UsbDevice d) {
        log("Подключение CDC ACM...");
        worker.execute(() -> {
            try {
                transport.open(d);
                log("CDC ACM открыт, 115200 8N1. Чтение Radio Info...");
                OpenGd77Protocol.FirmwareInfo fi = protocol.readFirmwareInfo();
                if (fi.radioType != 5) throw new IllegalStateException("Подключено не MD-9600: radioType=" + fi.radioType);
                log("MD-9600 найден, FW: " + fi.fwRevision + ", info v" + fi.structVersion);

                protocol.enterProgrammingMode(false);
                try {
                    originalAdditional = protocol.readFlash(AdditionalSettingsImage.FLASH_BASE, AdditionalSettingsImage.READ_SIZE);
                    AdditionalSettingsImage img = new AdditionalSettingsImage(originalAdditional);
                    AdditionalSettingsImage.Tlv sat = img.findTlv(3);
                    if (sat == null) throw new IllegalStateException("Satellite TLV ID 3 не найден");
                    log("Satellite TLV: offset 0x" + Integer.toHexString(sat.headerOffset)
                            + ", payload 0x" + Integer.toHexString(sat.payloadLength));
                } finally {
                    protocol.closeProgrammingMode();
                }
                updateWriteButton();
            } catch (Exception e) {
                log("USB/read error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                try { transport.close(); } catch (Exception ignored) {}
            }
        });
    }

    private void confirmUpdate() {
        if (prepared == null || originalAdditional == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Записать Keps?")
                .setMessage("Будут изменены только спутниковые данные TLV ID 3. Перед записью два сектора FLASH читаются и сохраняются в RAM; соседние данные не затираются.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Записать", (d, w) -> writeUpdate())
                .show();
    }

    private void writeUpdate() {
        updateButton.setEnabled(false);
        worker.execute(() -> {
            try {
                byte[] before = Arrays.copyOf(originalAdditional, originalAdditional.length);
                AdditionalSettingsImage img = new AdditionalSettingsImage(before);
                img.replaceSatellitePayload(prepared.payload);
                byte[] after = img.bytes();

                protocol.enterProgrammingMode(true);
                try {
                    int changed = 0;
                    for (int s = 0; s < 2; s++) {
                        byte[] oldSector = Arrays.copyOfRange(before, s * 4096, (s + 1) * 4096);
                        byte[] newSector = Arrays.copyOfRange(after, s * 4096, (s + 1) * 4096);
                        if (!Arrays.equals(oldSector, newSector)) {
                            changed++;
                            log("Запись FLASH sector 0x" + Integer.toHexString(0x20 + s) + "...");
                            protocol.writeFlashSector(AdditionalSettingsImage.FLASH_BASE + s * 4096, newSector);
                        }
                    }
                    log("Записано изменённых секторов: " + changed);
                } finally {
                    protocol.closeProgrammingMode();
                }

                protocol.reboot();
                log("Keps записаны. Радиостанция перезагружается.");
                originalAdditional = null;
            } catch (Exception e) {
                log("ОШИБКА ЗАПИСИ: " + e.getMessage());
            } finally {
                updateWriteButton();
            }
        });
    }

    private void updateWriteButton() {
        runOnUiThread(() -> updateButton.setEnabled(prepared != null && originalAdditional != null));
    }

    private void log(final String s) {
        runOnUiThread(() -> {
            String old = status.getText().toString();
            status.setText(old + "\n" + s);
        });
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(usbReceiver); } catch (Exception ignored) {}
        worker.shutdownNow();
        transport.close();
        super.onDestroy();
    }
}
