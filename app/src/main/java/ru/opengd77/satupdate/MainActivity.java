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
    private Button connectButton;
    private Button dryRunButton;
    private Button updateButton;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private UsbManager usbManager;
    private UsbCdcSerialTransport transport;
    private OpenGd77Protocol protocol;
    private List<SatelliteConfig> configs;
    private OpenGd77SatelliteEncoder.BuildResult prepared;
    private byte[] originalAdditional;
    private UpdatePlan updatePlan;
    private OpenGd77Protocol.FirmwareInfo firmwareInfo;
    private volatile boolean radioBusy = false;
    private volatile boolean usbPermissionPending = false;
    private UsbDevice pendingPermissionDevice;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!USB_PERMISSION.equals(intent.getAction())) return;

            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (d == null) d = pendingPermissionDevice;

            usbPermissionPending = false;
            pendingPermissionDevice = null;

            if (granted && d != null) {
                log("Android разрешил доступ к USB. Продолжаю чтение...");
                connectAndRead(d);
            } else {
                log("USB permission denied/cancelled");
                setBusy(false, true);
            }
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
        connectButton = findViewById(R.id.connectButton);
        dryRunButton = findViewById(R.id.dryRunButton);
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
            log("OpenGD77 Satellite Updater v" + BuildConfig.VERSION_NAME);
            log("Загружено конфигураций Satellites.txt: " + configs.size());
        } catch (Exception e) {
            log("Ошибка Satellites.txt: " + e.getMessage());
        }

        findViewById(R.id.downloadButton).setOnClickListener(v -> downloadTle());
        findViewById(R.id.openFileButton).setOnClickListener(v -> openLocalTle());
        connectButton.setOnClickListener(v -> requestConnect());
        dryRunButton.setOnClickListener(v -> runDryRun());
        updateButton.setOnClickListener(v -> confirmUpdate());

        IntentFilter f = new IntentFilter(USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbReceiver, f);
        updateButtons();
    }

    @Override protected void onResume() {
        super.onResume();

        // OEM fallback. Normally UsbManager returns the result through usbReceiver.
        // Some Android builds resume the Activity after the permission dialog without
        // delivering the callback reliably. In that case use UsbManager.hasPermission().
        if (!usbPermissionPending || pendingPermissionDevice == null) return;

        UsbDevice d = pendingPermissionDevice;
        if (usbManager.hasPermission(d)) {
            usbPermissionPending = false;
            pendingPermissionDevice = null;
            log("USB permission подтвержден после системного диалога. Продолжаю чтение...");
            connectAndRead(d);
        } else {
            // onResume after a dismissed/denied system permission dialog: never leave
            // the UI permanently locked in radioBusy state.
            usbPermissionPending = false;
            pendingPermissionDevice = null;
            log("USB permission не получен. Можно повторить подключение.");
            setBusy(false, true);
        }
    }

    private void downloadTle() {
        final String url = urlEdit.getText().toString().trim();
        log("Загрузка: " + url);
        worker.execute(() -> {
            try {
                prepareTle(NetworkFetcher.get(url));
            } catch (Exception e) {
                log("Ошибка загрузки: " + e.getMessage());
            }
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
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                prepareTle(new String(out.toByteArray(), StandardCharsets.US_ASCII));
            } catch (Exception e) {
                log("Ошибка файла TLE: " + e.getMessage());
            }
        });
    }

    private void prepareTle(String text) {
        try {
            Map<Integer, TleEntry> all = TleParser.parse(text);
            prepared = OpenGd77SatelliteEncoder.buildPayload(configs, all);
            updatePlan = null;
            StringBuilder b = new StringBuilder();
            b.append("TLE записей в источнике: ").append(all.size()).append('\n');
            b.append("Будут загружены: ").append(prepared.loaded.size()).append('\n');
            for (String s : prepared.loaded) b.append("  + ").append(s).append('\n');
            if (!prepared.missing.isEmpty()) {
                b.append("Не найдены: ").append(prepared.missing.size()).append('\n');
                for (String s : prepared.missing) b.append("  - ").append(s).append('\n');
            }
            b.append("Для разрешения записи выполните Dry Run.");
            log(b.toString());
            updateButtons();
        } catch (Exception e) {
            log("Ошибка разбора TLE: " + e.getMessage());
        }
    }

    private void requestConnect() {
        if (radioBusy) {
            log("Подключение/чтение уже выполняется — повторное нажатие игнорировано.");
            return;
        }
        UsbDevice d = transport.findDevice();
        if (d == null) {
            log("MD-9600/OpenGD77 USB 1FC9:0094 не найден");
            return;
        }

        setBusy(true, false);
        log(transport.describeDevice(d));

        if (usbManager.hasPermission(d)) {
            connectAndRead(d);
            return;
        }

        pendingPermissionDevice = d;
        usbPermissionPending = true;

        Intent permissionIntent = new Intent(USB_PERMISSION);
        permissionIntent.setPackage(getPackageName());
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, permissionIntent, flags);

        log("Запрашивается системное разрешение Android на доступ к USB...");
        try {
            usbManager.requestPermission(d, pi);
        } catch (Exception e) {
            usbPermissionPending = false;
            pendingPermissionDevice = null;
            log("Ошибка запроса USB permission: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            setBusy(false, true);
        }
    }

    private void connectAndRead(UsbDevice d) {
        log("Подключение CDC ACM...");
        worker.execute(() -> {
            boolean ok = false;
            try {
                try { transport.close(); } catch (Exception ignored) {}
                transport.open(d);
                log("CDC ACM открыт, 115200 8N1. Чтение Radio Info...");
                OpenGd77Protocol.FirmwareInfo fi = protocol.readFirmwareInfo();
                if (fi.radioType != 5) throw new IllegalStateException("Подключено не MD-9600: radioType=" + fi.radioType);
                firmwareInfo = fi;
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
                updatePlan = null;
                ok = true;
                log("Чтение завершено. Dry Run готов к запуску.");
            } catch (Exception e) {
                log("USB/read error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                originalAdditional = null;
                updatePlan = null;
                try { transport.close(); } catch (Exception ignored) {}
            } finally {
                setBusy(false, ok);
            }
        });
    }

    private void runDryRun() {
        if (radioBusy) {
            log("Dry Run: дождитесь завершения текущей операции.");
            return;
        }
        if (prepared == null) {
            log("Dry Run: сначала загрузите TLE.");
            return;
        }
        if (originalAdditional == null) {
            log("Dry Run: сначала подключите MD-9600 и выполните чтение.");
            return;
        }

        setBusy(true, false);
        worker.execute(() -> {
            try {
                UpdatePlan plan = UpdatePlan.build(originalAdditional, prepared.payload);
                updatePlan = plan;
                log(formatDryRun(plan));
            } catch (Exception e) {
                updatePlan = null;
                log("DRY RUN ОШИБКА: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                setBusy(false, false);
            }
        });
    }

    private String formatDryRun(UpdatePlan plan) {
        StringBuilder b = new StringBuilder();
        b.append("\n=== DRY RUN — ЗАПИСЬ НЕ ВЫПОЛНЯЛАСЬ ===\n");
        if (firmwareInfo != null) {
            b.append("MD-9600 / FW ").append(firmwareInfo.fwRevision)
                    .append(" / Radio Info v").append(firmwareInfo.structVersion).append('\n');
        }
        b.append("Satellite TLV header: 0x").append(Integer.toHexString(plan.satelliteAbsoluteHeaderAddress())).append('\n');
        b.append("Payload: 0x").append(Integer.toHexString(plan.satelliteTlv.payloadLength))
                .append(" bytes, 0x").append(Integer.toHexString(plan.satelliteAbsolutePayloadStart()))
                .append("..0x").append(Integer.toHexString(plan.satelliteAbsolutePayloadEndInclusive())).append('\n');
        b.append("Спутников сейчас: ").append(plan.currentSatelliteCount).append('\n');
        b.append("Спутников после обновления: ").append(plan.newSatelliteCount).append('\n');
        b.append("Изменённых SatelliteElement: ").append(plan.changedRecords.size()).append('\n');
        for (String name : plan.changedRecords) b.append("  * ").append(name).append('\n');
        b.append("Изменённых байт: ").append(plan.changedBytes).append('\n');
        b.append("FLASH sectors:\n");
        for (int s = 0; s < 2; s++) {
            boolean changed = plan.changedSectorIndexes.contains(s);
            b.append("  0x").append(Integer.toHexString(0x20 + s)).append(": ")
                    .append(changed ? "CHANGED" : "unchanged").append('\n');
        }
        b.append("Операция записи: ").append(plan.changedSectorIndexes.size()).append(" sector(s), ")
                .append(plan.changedSectorIndexes.size() * AdditionalSettingsImage.SECTOR_SIZE).append(" bytes\n");
        b.append("Проверка границ TLV: OK — изменяться могут только орбитальные байты 0x08..0x2F существующих записей.\n");
        if (plan.changedBytes == 0) b.append("Keps уже совпадают — запись не требуется.\n");
        else b.append("Dry Run OK. Кнопка записи разблокирована.\n");
        return b.toString();
    }

    private void confirmUpdate() {
        if (updatePlan == null || updatePlan.changedBytes == 0 || radioBusy) return;
        final UpdatePlan plan = updatePlan;
        StringBuilder message = new StringBuilder();
        message.append("Dry Run успешно завершён.\n\nИзменённых байт: ").append(plan.changedBytes)
                .append("\nСекторов FLASH: ").append(plan.changedSectorIndexes.size())
                .append("\n\nПеред записью станция будет считана повторно. После записи каждый сектор будет полностью проверен read-back сравнением.");
        new AlertDialog.Builder(this)
                .setTitle("Записать Keps?")
                .setMessage(message.toString())
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Записать", (d, w) -> writeUpdate(plan))
                .show();
    }

    private void writeUpdate(UpdatePlan plan) {
        setBusy(true, false);
        worker.execute(() -> {
            try {
                log("\nКонтроль перед записью: повторное чтение 0x20000..0x21FFF...");
                byte[] fresh;
                protocol.enterProgrammingMode(false);
                try {
                    fresh = protocol.readFlash(AdditionalSettingsImage.FLASH_BASE, AdditionalSettingsImage.READ_SIZE);
                } finally {
                    protocol.closeProgrammingMode();
                }
                if (!Arrays.equals(fresh, plan.beforeImage)) {
                    originalAdditional = fresh;
                    updatePlan = null;
                    int diff = firstDiff(fresh, plan.beforeImage);
                    throw new IllegalStateException("FLASH изменился после Dry Run (первое отличие +0x"
                            + Integer.toHexString(diff) + "). Выполните Dry Run повторно; запись отменена.");
                }
                log("Контроль перед записью: OK, FLASH не изменился.");

                if (plan.changedSectorIndexes.isEmpty()) {
                    log("Изменений нет. Запись не требуется.");
                    return;
                }

                protocol.enterProgrammingMode(true);
                try {
                    for (int sectorIndex : plan.changedSectorIndexes) {
                        int address = AdditionalSettingsImage.FLASH_BASE
                                + sectorIndex * AdditionalSettingsImage.SECTOR_SIZE;
                        int sectorNo = address / AdditionalSettingsImage.SECTOR_SIZE;
                        byte[] expected = Arrays.copyOfRange(plan.afterImage,
                                sectorIndex * AdditionalSettingsImage.SECTOR_SIZE,
                                (sectorIndex + 1) * AdditionalSettingsImage.SECTOR_SIZE);

                        log("Запись FLASH sector 0x" + Integer.toHexString(sectorNo) + "...");
                        protocol.writeFlashSector(address, expected);

                        log("Read-back sector 0x" + Integer.toHexString(sectorNo) + "...");
                        byte[] verified = protocol.readFlash(address, AdditionalSettingsImage.SECTOR_SIZE);
                        if (!Arrays.equals(expected, verified)) {
                            int diff = firstDiff(expected, verified);
                            throw new IllegalStateException("READ-BACK FAILED sector 0x"
                                    + Integer.toHexString(sectorNo) + " at +0x" + Integer.toHexString(diff));
                        }
                        log("Read-back sector 0x" + Integer.toHexString(sectorNo) + ": OK (4096/4096 bytes)");
                    }
                } finally {
                    protocol.closeProgrammingMode();
                }

                originalAdditional = Arrays.copyOf(plan.afterImage, plan.afterImage.length);
                updatePlan = null;
                log("Все изменённые сектора подтверждены read-back сравнением.");
                protocol.reboot();
                log("Keps записаны и проверены. Радиостанция перезагружается.");
                originalAdditional = null;
            } catch (Exception e) {
                log("ОШИБКА ЗАПИСИ/ПРОВЕРКИ: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                setBusy(false, false);
            }
        });
    }

    private static int firstDiff(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) if (a[i] != b[i]) return i;
        return n;
    }

    private void setBusy(final boolean busy, final boolean logReadyState) {
        runOnUiThread(() -> {
            radioBusy = busy;
            applyButtonState();
            if (!busy && logReadyState) {
                appendLogDirect("UI: Подключить=ON, Dry Run=ON");
            }
        });
    }

    private void updateButtons() {
        runOnUiThread(this::applyButtonState);
    }

    private void applyButtonState() {
        connectButton.setEnabled(!radioBusy);
        dryRunButton.setEnabled(!radioBusy);
        updateButton.setEnabled(!radioBusy && updatePlan != null && updatePlan.changedBytes > 0);
    }

    private void log(final String s) {
        runOnUiThread(() -> appendLogDirect(s));
    }

    private void appendLogDirect(String s) {
        String old = status.getText().toString();
        status.setText(old + "\n" + s);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(usbReceiver); } catch (Exception ignored) {}
        worker.shutdownNow();
        transport.close();
        super.onDestroy();
    }
}
