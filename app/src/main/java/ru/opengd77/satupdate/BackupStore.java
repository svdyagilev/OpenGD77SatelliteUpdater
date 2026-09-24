package ru.opengd77.satupdate;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class BackupStore {
    private BackupStore() {}

    static File saveAdditionalSettings(Context context, byte[] image, RadioDriver.Identity identity) throws IOException {
        if (image == null || image.length != AdditionalSettingsImage.READ_SIZE) {
            throw new IOException("Backup image must be exactly 0x2000 bytes");
        }

        File root = context.getExternalFilesDir("backups");
        if (root == null) root = new File(context.getFilesDir(), "backups");
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Cannot create backup directory: " + root.getAbsolutePath());
        }

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String model = identity == null ? "radio" : identity.model.replaceAll("[^A-Za-z0-9_-]", "_");
        File out = new File(root, model + "_additional_" + stamp + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(image);
            fos.flush();
        }
        return out;
    }
}
