package ru.opengd77.satupdate;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
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
    private TextView versionText;
    private TextView radioSummaryText;
    private TextView tleSummaryText;
    private TextView operationSummaryText;
    private Button autoUpdateButton;
    private Button connectButton;
    private Button dryRunButton;
    private Button updateButton;
    private Button downloadButton;
    private Button openFileButton;
    private Button advancedToggleButton;
    private Button diagnosticsToggleButton;
    private LinearLayout advancedContainer;
    private LinearLayout diagnosticsContainer;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private UsbManager usbManager;
    private UsbCdcSerialTransport transport;
    private OpenGd77Protocol protocol;
    private RadioDriver driver;
    private List<SatelliteConfig> configs;
    private Map<Integer, TleEntry> tleByCatalog;
    private OpenGd77SatelliteEncoder.BuildResult prepared;
    private byte[] originalAdditional;
    private UpdatePlan updatePlan;
    private RadioDriver.Identity radioIdentity;
    private SatelliteBankInspector.Summary bankSummary;
    private File lastBackupFile;
    private String tleSourceLabel = "";

    private volatile boolean radioBusy = false;
    private volatile boolean usbPermissionPending = false;
    private UsbDevice pendingPermissionDevice;
    private boolean pendingPermissionAuto = false;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!USB_PERMISSION.equals(intent.getAction())) return;
            if (!usbPermissionPending) {
                log("Повторный USB permission callback проигнорирован.");
                return;
            }

            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (d == null) d = pendingPermissionDevice;
            boolean automatic = pendingPermissionAuto;

            usbPermissionPending = false;
            pendingPermissionDevice = null;
            pendingPermissionAuto = false;

            if (granted && d != null) {
                log("Android разрешил доступ к USB. Продолжаю чтение...");
                connectAndRead(d, automatic);
            } else {
                log("USB permission denied/cancelled");
                setOperationSummary("Доступ к USB не разрешён");
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
        driver = new Md9600Driver(protocol);

        sourceSpinner = findViewById(R.id.sourceSpinner);
        urlEdit = findViewById(R.id.urlEdit);
        status = findViewById(R.id.statusText);
        versionText = findViewById(R.id.versionText);
        radioSummaryText = findViewById(R.id.radioSummaryText);
        tleSummaryText = findViewById(R.id.tleSummaryText);
        operationSummaryText = findViewById(R.id.operationSummaryText);
        autoUpdateButton = findViewById(R.id.autoUpdateButton);
        connectButton = findViewById(R.id.connectButton);
        dryRunButton = findViewById(R.id.dryRunButton);
        updateButton = findViewById(R.id.updateButton);
        downloadButton = findViewById(R.id.downloadButton);
        openFileButton = findViewById(R.id.openFileButton);
        advancedToggleButton = findViewById(R.id.advancedToggleButton);
        diagnosticsToggleButton = findViewById(R.id.diagnosticsToggleButton);
        advancedContainer = findViewById(R.id.advancedContainer);
        diagnosticsContainer = findViewById(R.id.diagnosticsContainer);

        versionText.setText("v" + BuildConfig.VERSION_NAME + " • Satellite/Keps • Android 6+");

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
            log("OpenGD77 CPS Android v" + BuildConfig.VERSION_NAME);
            log("Satellite module: конфигураций Satellites.txt: " + configs.size());
        } catch (Exception e) {
            log("Ошибка Satellites.txt: " + e.getMessage());
        }

        autoUpdateButton.setOnClickListener(v -> startAutomaticUpdate());
        downloadButton.setOnClickListener(v -> downloadTle());
        openFileButton.setOnClickListener(v -> openLocalTle());
        connectButton.setOnClickListener(v -> requestConnect());
        dryRunButton.setOnClickListener(v -> runDryRun());
        updateButton.setOnClickListener(v -> confirmUpdate());
        advancedToggleButton.setOnClickListener(v -> toggleSection(advancedContainer, advancedToggleButton, "Расширенный режим"));
        diagnosticsToggleButton.setOnClickListener(v -> toggleSection(diagnosticsContainer, diagnosticsToggleButton, "Диагностика"));

        IntentFilter f = new IntentFilter(USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbReceiver, f);

        updateButtons();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!usbPermissionPending || pendingPermissionDevice == null) return;

        UsbDevice d = pendingPermissionDevice;
        boolean automatic = pendingPermissionAuto;
        if (usbManager.hasPermission(d)) {
            usbPermissionPending = false;
            pendingPermissionDevice = null;
            pendingPermissionAuto = false;
            log("USB permission подтвержден после системного диалога. Продолжаю чтение...");
            connectAndRead(d, automatic);
        } else {
            usbPermissionPending = false;
            pendingPermissionDevice = null;
            pendingPermissionAuto = false;
            log("USB permission не получен. Можно повторить подключение.");
            setOperationSummary("Ожидание подключения радиостанции");
            setBusy(false, true);
        }
    }

    private void toggleSection(View section, Button button, String title) {
        boolean open = section.getVisibility() == View.VISIBLE;
        section.setVisibility(open ? View.GONE : View.VISIBLE);
        button.setText(title + (open ? " ▾" : " ▴"));
    }

    private void startAutomaticUpdate() {
        if (radioBusy) {
            log("Автоматическое обновление: другая операция уже выполняется.");
            return;
        }
        setBusy(true, false);
        setOperationSummary("1/4 • Загружаю свежие TLE...");
        log("\n=== АВТОМАТИЧЕСКОЕ ОБНОВЛЕНИЕ ===");
        worker.execute(() -> {
            boolean loaded = false;
            try {
                log("Основной источник: CelesTrak");
                prepareTleInternal(NetworkFetcher.get(CELESTRAK), "CelesTrak");
                if (prepared != null && !prepared.loaded.isEmpty()) loaded = true;
            } catch (Exception first) {
                log("CelesTrak недоступен/не принят: " + first.getMessage());
            }

            if (!loaded) {
                try {
                    log("Переключаюсь на резервный источник R4UAB...");
                    prepareTleInternal(NetworkFetcher.get(R4UAB), "R4UAB");
                    loaded = prepared != null && !prepared.loaded.isEmpty();
                } catch (Exception second) {
                    log("R4UAB недоступен/не принят: " + second.getMessage());
                }
            }

            if (!loaded) {
                setOperationSummary("Не удалось получить пригодные TLE");
                setBusy(false, false);
                return;
            }

            setOperationSummary("2/4 • Подключаюсь к радиостанции...");
            runOnUiThread(() -> requestConnectInternal(true));
        });
    }

    private void downloadTle() {
        if (radioBusy) return;
        final String url = urlEdit.getText().toString().trim();
        final String label = sourceSpinner.getSelectedItemPosition() == 0 ? "CelesTrak"
                : sourceSpinner.getSelectedItemPosition() == 1 ? "R4UAB" : "Свой URL";
        setOperationSummary("Загрузка TLE...");
        log("Загрузка: " + url);
        worker.execute(() -> {
            try {
                prepareTleInternal(NetworkFetcher.get(url), label);
                setOperationSummary("TLE загружены. Можно выполнить Dry Run.");
            } catch (Exception e) {
                log("Ошибка загрузки: " + e.getMessage());
                setOperationSummary("Ошибка загрузки TLE");
            }
        });
    }

    private void openLocalTle() {
        if (radioBusy) return;
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
                prepareTleInternal(new String(out.toByteArray(), StandardCharsets.US_ASCII), "Локальный файл");
                setOperationSummary("TLE из файла загружены. Можно выполнить Dry Run.");
            } catch (Exception e) {
                log("Ошибка файла TLE: " + e.getMessage());
                setOperationSummary("Ошибка чтения TLE-файла");
            }
        });
    }

    private void prepareTleInternal(String text, String sourceLabel) throws Exception {
        Map<Integer, TleEntry> all = TleParser.parse(text);
        OpenGd77SatelliteEncoder.BuildResult result = OpenGd77SatelliteEncoder.buildPayload(configs, all);
        if (result.loaded.isEmpty()) throw new IllegalStateException("нет свежих TLE для списка Satellites.txt");

        tleByCatalog = all;
        prepared = result;
        tleSourceLabel = sourceLabel;
        updatePlan = null;

        StringBuilder b = new StringBuilder();
        b.append("TLE источник: ").append(sourceLabel).append('\n');
        b.append("TLE записей в источнике: ").append(all.size()).append('\n');
        b.append("Пригодны для конфигурации: ").append(result.loaded.size()).append('/').append(configs.size()).append('\n');
        for (String s : result.loaded) b.append("  + ").append(s).append('\n');
        if (!result.missing.isEmpty()) {
            b.append("Пропущены: ").append(result.missing.size()).append('\n');
            for (String s : result.missing) b.append("  - ").append(s).append('\n');
        }
        log(b.toString());
        updateTleSummary();
        updateButtons();
    }

    private void updateTleSummary() {
        if (prepared == null || tleByCatalog == null) {
            runOnUiThread(() -> tleSummaryText.setText("TLE ещё не загружены"));
            return;
        }

        long now = System.currentTimeMillis();
        double newest = Double.POSITIVE_INFINITY;
        double oldest = Double.NEGATIVE_INFINITY;
        int valid = 0;
        for (SatelliteConfig cfg : configs) {
            TleEntry tle = tleByCatalog.get(cfg.catalogNumber);
            if (tle == null) continue;
            try {
                double age = tle.ageDays(now);
                if (age > OpenGd77SatelliteEncoder.MAX_TLE_AGE_DAYS) continue;
                newest = Math.min(newest, age);
                oldest = Math.max(oldest, age);
                valid++;
            } catch (RuntimeException ignored) {}
        }

        final String text;
        if (valid == 0) {
            text = tleSourceLabel + " • свежих TLE нет";
        } else {
            text = String.format(Locale.US, "%s • %d/%d спутн. • возраст %.1f–%.1f d",
                    tleSourceLabel, valid, configs.size(), newest, oldest);
        }
        runOnUiThread(() -> tleSummaryText.setText(text));
    }

    private void requestConnect() {
        if (radioBusy) {
            log("Подключение/чтение уже выполняется — повторное нажатие игнорировано.");
            return;
        }
        setBusy(true, false);
        setOperationSummary("Чтение радиостанции...");
        requestConnectInternal(false);
    }

    private void requestConnectInternal(boolean automatic) {
        UsbDevice d = transport.findDevice();
        if (d == null) {
            log("MD-9600/OpenGD77 USB 1FC9:0094 не найден");
            setOperationSummary("MD-9600 не найдена по USB");
            setBusy(false, false);
            return;
        }

        log(transport.describeDevice(d));
        if (usbManager.hasPermission(d)) {
            connectAndRead(d, automatic);
            return;
        }

        pendingPermissionDevice = d;
        pendingPermissionAuto = automatic;
        usbPermissionPending = true;

        Intent permissionIntent = new Intent(USB_PERMISSION);
        permissionIntent.setPackage(getPackageName());
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, permissionIntent, flags);

        log("Запрашивается системное разрешение Android на доступ к USB...");
        try {
            usbManager.requestPermission(d, pi);
        } catch (Exception e) {
            usbPermissionPending = false;
            pendingPermissionDevice = null;
            pendingPermissionAuto = false;
            log("Ошибка запроса USB permission: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            setOperationSummary("Ошибка USB permission");
            setBusy(false, true);
        }
    }

    private void connectAndRead(UsbDevice d, boolean automatic) {
        log("Подключение CDC ACM...");
        worker.execute(() -> {
            boolean ok = false;
            UpdatePlan automaticPlan = null;
            try {
                try { transport.close(); } catch (Exception ignored) {}
                transport.open(d);
                log("CDC ACM открыт, 115200 8N1. Чтение Radio Info...");

                radioIdentity = driver.identify();
                log(radioIdentity.compactText());

                originalAdditional = driver.readAdditionalSettings();
                AdditionalSettingsImage img = new AdditionalSettingsImage(originalAdditional);
                AdditionalSettingsImage.Tlv sat = img.findTlv(3);
                if (sat == null) throw new IllegalStateException("Satellite TLV ID 3 не найден");
                log("Satellite TLV: offset 0x" + Integer.toHexString(sat.headerOffset)
                        + ", payload 0x" + Integer.toHexString(sat.payloadLength));

                bankSummary = SatelliteBankInspector.inspect(originalAdditional, System.currentTimeMillis());
                updateRadioSummary();
                updatePlan = null;
                ok = true;
                log("Чтение завершено. " + bankSummary.compactText());

                if (automatic) {
                    if (prepared == null) throw new IllegalStateException("Автообновление: TLE не подготовлены");
                    setOperationSummary("3/4 • Проверяю изменения (Dry Run)...");
                    automaticPlan = UpdatePlan.build(originalAdditional, prepared.payload);
                    updatePlan = automaticPlan;
                    log(formatDryRun(automaticPlan));
                    if (automaticPlan.changedBytes == 0) {
                        setOperationSummary("Keps уже актуальны. Запись не требуется.");
                    } else {
                        setOperationSummary("Найдено обновление: " + automaticPlan.changedRecords.size()
                                + " спутн., " + automaticPlan.changedBytes + " байт");
                    }
                } else {
                    setOperationSummary("Радиостанция прочитана. Готово к Dry Run.");
                }
            } catch (Exception e) {
                log("USB/read error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                originalAdditional = null;
                updatePlan = null;
                bankSummary = null;
                try { transport.close(); } catch (Exception ignored) {}
                setOperationSummary("Ошибка чтения радиостанции");
            } finally {
                setBusy(false, ok);
            }

            final UpdatePlan planForDialog = automaticPlan;
            if (ok && automatic && planForDialog != null) {
                runOnUiThread(() -> showAutomaticResult(planForDialog));
            }
        });
    }

    private void updateRadioSummary() {
        if (radioIdentity == null) return;
        String text = radioIdentity.compactText();
        if (bankSummary != null) text += "\n" + bankSummary.compactText();
        final String finalText = text;
        runOnUiThread(() -> radioSummaryText.setText(finalText));
    }

    private void runDryRun() {
        if (radioBusy) {
            log("Dry Run: дождитесь завершения текущей операции.");
            return;
        }
        if (prepared == null) {
            log("Dry Run: сначала загрузите TLE.");
            setOperationSummary("Сначала загрузите TLE");
            return;
        }
        if (originalAdditional == null) {
            log("Dry Run: сначала подключите MD-9600 и выполните чтение.");
            setOperationSummary("Сначала прочитайте радиостанцию");
            return;
        }

        setBusy(true, false);
        setOperationSummary("Dry Run...");
        worker.execute(() -> {
            try {
                UpdatePlan plan = UpdatePlan.build(originalAdditional, prepared.payload);
                updatePlan = plan;
                log(formatDryRun(plan));
                if (plan.changedBytes == 0) setOperationSummary("Keps уже актуальны. Запись не требуется.");
                else setOperationSummary("Dry Run OK: " + plan.changedRecords.size() + " спутн., "
                        + plan.changedBytes + " байт изменятся");
            } catch (Exception e) {
                updatePlan = null;
                log("DRY RUN ОШИБКА: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                setOperationSummary("Dry Run завершился ошибкой");
            } finally {
                setBusy(false, false);
            }
        });
    }

    private String formatDryRun(UpdatePlan plan) {
        StringBuilder b = new StringBuilder();
        b.append("\n=== DRY RUN — ЗАПИСЬ НЕ ВЫПОЛНЯЛАСЬ ===\n");
        if (radioIdentity != null) b.append(radioIdentity.compactText()).append('\n');
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
        b.append("Проверка границ TLV: OK — только orbital bytes 0x08..0x2F существующих записей.\n");
        return b.toString();
    }

    private void showAutomaticResult(UpdatePlan plan) {
        if (plan.changedBytes == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Keps актуальны")
                    .setMessage("Данные в радиостанции уже совпадают со свежими TLE. Запись во FLASH не требуется.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String message = "Источник: " + tleSourceLabel
                + "\nСпутников обновится: " + plan.changedRecords.size()
                + "\nИзменённых байт: " + plan.changedBytes
                + "\nFLASH-секторов: " + plan.changedSectorIndexes.size()
                + "\n\nПеред записью автоматически будет создан backup 0x2000 байт, затем выполнены повторное чтение и полный read-back контроль.";

        new AlertDialog.Builder(this)
                .setTitle("Обновить Keps?")
                .setMessage(message)
                .setNegativeButton("Отмена", (d, w) -> setOperationSummary("Обновление отменено пользователем"))
                .setPositiveButton("Записать", (d, w) -> writeUpdate(plan))
                .show();
    }

    private void confirmUpdate() {
        if (updatePlan == null || updatePlan.changedBytes == 0 || radioBusy) return;
        final UpdatePlan plan = updatePlan;
        String message = "Dry Run успешно завершён."
                + "\n\nИзменённых байт: " + plan.changedBytes
                + "\nСекторов FLASH: " + plan.changedSectorIndexes.size()
                + "\n\nПеред записью будет создан backup, FLASH считан повторно и каждый записанный сектор проверен read-back сравнением.";
        new AlertDialog.Builder(this)
                .setTitle("Записать Keps?")
                .setMessage(message)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Записать", (d, w) -> writeUpdate(plan))
                .show();
    }

    private void writeUpdate(UpdatePlan plan) {
        setBusy(true, false);
        setOperationSummary("4/4 • Backup и запись Keps...");
        worker.execute(() -> {
            try {
                lastBackupFile = BackupStore.saveAdditionalSettings(this, plan.beforeImage, radioIdentity);
                log("Backup перед записью: " + lastBackupFile.getAbsolutePath()
                        + " (" + plan.beforeImage.length + " bytes)");

                driver.writeVerified(plan, this::log);

                originalAdditional = plan.afterImage.clone();
                updatePlan = null;
                bankSummary = SatelliteBankInspector.inspect(originalAdditional, System.currentTimeMillis());
                updateRadioSummary();

                driver.reboot();
                log("Keps записаны и проверены. Радиостанция перезагружается.");
                setOperationSummary("Готово • Keps записаны и проверены");
                originalAdditional = null;
            } catch (Exception e) {
                log("ОШИБКА ЗАПИСИ/ПРОВЕРКИ: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                setOperationSummary("Ошибка записи/проверки — см. диагностику");
            } finally {
                setBusy(false, false);
            }
        });
    }

    private void setBusy(final boolean busy, final boolean logReadyState) {
        runOnUiThread(() -> {
            radioBusy = busy;
            applyButtonState();
            if (!busy && logReadyState) appendLogDirect("UI: основные кнопки активны");
        });
    }

    private void updateButtons() {
        runOnUiThread(this::applyButtonState);
    }

    private void applyButtonState() {
        autoUpdateButton.setEnabled(!radioBusy);
        connectButton.setEnabled(!radioBusy);
        dryRunButton.setEnabled(!radioBusy);
        downloadButton.setEnabled(!radioBusy);
        openFileButton.setEnabled(!radioBusy);
        updateButton.setEnabled(!radioBusy && updatePlan != null && updatePlan.changedBytes > 0);
    }

    private void setOperationSummary(final String text) {
        runOnUiThread(() -> operationSummaryText.setText(text));
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
