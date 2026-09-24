package ru.opengd77.satupdate;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Isolated read-only codeplug session. It owns its USB connection and never exposes a write path.
 */
public class CodeplugReadActivity extends Activity {
    private static final String USB_PERMISSION = "ru.opengd77.satupdate.USB_PERMISSION_CODEPLUG";

    private TextView status;
    private Button retryButton;
    private UsbManager usbManager;
    private UsbCdcSerialTransport transport;
    private OpenGd77Protocol protocol;
    private RadioDriver driver;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private volatile boolean busy = false;
    private volatile boolean permissionPending = false;
    private UsbDevice pendingDevice;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!USB_PERMISSION.equals(intent.getAction()) || !permissionPending) return;
            UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (d == null) d = pendingDevice;
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            permissionPending = false;
            pendingDevice = null;
            if (granted && d != null) {
                log("Android разрешил доступ к USB.");
                readDevice(d);
            } else {
                fail("Доступ к USB не разрешён.");
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_codeplug_read);

        status = findViewById(R.id.codeplugReadStatus);
        retryButton = findViewById(R.id.codeplugReadRetryButton);
        findViewById(R.id.codeplugReadBackButton).setOnClickListener(v -> returnToMain());
        retryButton.setOnClickListener(v -> beginRead());

        usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        transport = new UsbCdcSerialTransport(usbManager);
        protocol = new OpenGd77Protocol(transport);
        driver = new Md9600Driver(protocol);

        IntentFilter f = new IntentFilter(USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbReceiver, f);

        log("OpenGD77 CPS Android v" + BuildConfig.VERSION_NAME);
        log("v0.5 Codeplug Viewer • только чтение");
        log("Полная запись codeplug в этой версии отключена.");

        getWindow().getDecorView().post(this::beginRead);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!permissionPending || pendingDevice == null) return;
        UsbDevice d = pendingDevice;
        if (usbManager.hasPermission(d)) {
            permissionPending = false;
            pendingDevice = null;
            log("USB permission подтверждён после системного диалога.");
            readDevice(d);
        }
    }

    @Override public void onBackPressed() {
        returnToMain();
    }

    private void returnToMain() {
        try { transport.close(); } catch (Exception ignored) {}
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void beginRead() {
        if (busy) return;
        busy = true;
        retryButton.setEnabled(false);
        CodeplugSession.current = null;
        log("\nПоиск OpenGD77 USB 1FC9:0094...");

        UsbDevice d = transport.findDevice();
        if (d == null) {
            fail("Радиостанция OpenGD77/MD-9600 не найдена по USB.");
            return;
        }
        log(transport.describeDevice(d));

        if (usbManager.hasPermission(d)) {
            readDevice(d);
            return;
        }

        permissionPending = true;
        pendingDevice = d;
        Intent permissionIntent = new Intent(USB_PERMISSION);
        permissionIntent.setPackage(getPackageName());
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, permissionIntent, flags);
        log("Запрашивается разрешение Android на USB...");
        try {
            usbManager.requestPermission(d, pi);
        } catch (Exception e) {
            permissionPending = false;
            pendingDevice = null;
            fail("Ошибка USB permission: " + e.getMessage());
        }
    }

    private void readDevice(UsbDevice d) {
        worker.execute(() -> {
            try {
                try { transport.close(); } catch (Exception ignored) {}
                log("Подключение CDC ACM 115200 8N1...");
                transport.open(d);
                RadioDriver.Identity identity = driver.identify();
                log(identity.compactText());
                log("Начинаю чтение основных блоков codeplug...");

                CodeplugSnapshot raw = driver.readCodeplug(this::log);
                log("Декодирование...");
                CodeplugModel model = OpenGd77CodeplugDecoder.decode(raw);
                CodeplugSession.current = model;
                log("OK: " + model.compactSummary());
                log("Radio name: " + model.general.radioName + " • DMR ID " + model.general.dmrId);
                log("Запись во FLASH/EEPROM не выполнялась.");

                runOnUiThread(() -> {
                    busy = false;
                    retryButton.setEnabled(true);
                    startActivity(new Intent(this, CodeplugViewerActivity.class));
                    finish();
                });
            } catch (Exception e) {
                try { transport.close(); } catch (Exception ignored) {}
                fail("Ошибка чтения codeplug: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void fail(String text) {
        log(text);
        runOnUiThread(() -> {
            busy = false;
            retryButton.setEnabled(true);
        });
    }

    private void log(String text) {
        runOnUiThread(() -> status.setText(status.getText().toString() + "\n" + text));
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(usbReceiver); } catch (Exception ignored) {}
        try { transport.close(); } catch (Exception ignored) {}
        worker.shutdownNow();
        super.onDestroy();
    }
}
