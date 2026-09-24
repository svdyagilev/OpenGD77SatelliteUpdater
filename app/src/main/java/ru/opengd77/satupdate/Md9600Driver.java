package ru.opengd77.satupdate;

import java.io.IOException;
import java.util.Arrays;

final class Md9600Driver implements RadioDriver {
    private static final int FLASH_CODEPLUG_BASE = 0x20000;

    private final OpenGd77Protocol protocol;

    Md9600Driver(OpenGd77Protocol protocol) {
        this.protocol = protocol;
    }

    @Override public Identity identify() throws IOException {
        OpenGd77Protocol.FirmwareInfo fi = protocol.readFirmwareInfo();
        if (fi.radioType != 5) {
            throw new IOException("Подключено не MD-9600: radioType=" + fi.radioType);
        }
        return new Identity("MD-9600", fi.radioType, fi.structVersion, fi.fwRevision);
    }

    @Override public byte[] readAdditionalSettings() throws IOException {
        protocol.enterProgrammingMode(false);
        try {
            return protocol.readFlash(AdditionalSettingsImage.FLASH_BASE, AdditionalSettingsImage.READ_SIZE);
        } finally {
            protocol.closeProgrammingMode();
        }
    }

    @Override public CodeplugSnapshot readCodeplug(Progress progress) throws IOException {
        if (progress == null) progress = text -> {};
        protocol.enterProgrammingMode(false, "Reading Codeplug");
        try {
            progress.onMessage("Codeplug 1/7: General Settings (EEPROM 0x00E0)...");
            byte[] general = protocol.readEeprom(0x00E0, 0x28);

            progress.onMessage("Codeplug 2/7: Scan Lists (EEPROM 0x1790)...");
            byte[] scanLists = protocol.readEeprom(0x1790, 0x1640);

            progress.onMessage("Codeplug 3/7: Channels 1-128 (EEPROM 0x3780)...");
            byte[] channelBank0 = protocol.readEeprom(0x3780, 0x1C10);

            progress.onMessage("Codeplug 4/7: Zones, до 250 (EEPROM 0x8010)...");
            byte[] zones = protocol.readEeprom(0x8010, 0xAC00);

            progress.onMessage("Codeplug 5/7: Channels 129-1024 (FLASH logical 0x7B1B0)...");
            byte[] channelBanks = protocol.readFlash(FLASH_CODEPLUG_BASE + 0x7B1B0, 0xC470);

            progress.onMessage("Codeplug 6/7: DMR Contacts (FLASH logical 0x87620)...");
            byte[] contacts = protocol.readFlash(FLASH_CODEPLUG_BASE + 0x87620, 0x6000);

            progress.onMessage("Codeplug 7/7: RX Groups (FLASH logical 0x8D620)...");
            byte[] rxGroups = protocol.readFlash(FLASH_CODEPLUG_BASE + 0x8D620, 0x1840);

            CodeplugSnapshot snapshot = new CodeplugSnapshot(general, scanLists, channelBank0,
                    zones, channelBanks, contacts, rxGroups);
            progress.onMessage("Codeplug прочитан: " + snapshot.totalBytes() + " bytes.");
            return snapshot;
        } finally {
            protocol.closeProgrammingMode();
        }
    }

    @Override public void writeVerified(UpdatePlan plan, Progress progress) throws IOException {
        if (progress == null) progress = text -> {};

        progress.onMessage("Контроль перед записью: повторное чтение 0x20000..0x21FFF...");
        byte[] fresh = readAdditionalSettings();
        if (!Arrays.equals(fresh, plan.beforeImage)) {
            int diff = firstDiff(fresh, plan.beforeImage);
            throw new IOException("FLASH изменился после Dry Run (первое отличие +0x"
                    + Integer.toHexString(diff) + "). Выполните Dry Run повторно; запись отменена.");
        }
        progress.onMessage("Контроль перед записью: OK, FLASH не изменился.");

        if (plan.changedSectorIndexes.isEmpty()) {
            progress.onMessage("Изменений нет. Запись не требуется.");
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

                progress.onMessage("Запись FLASH sector 0x" + Integer.toHexString(sectorNo) + "...");
                protocol.writeFlashSector(address, expected);

                progress.onMessage("Read-back sector 0x" + Integer.toHexString(sectorNo) + "...");
                byte[] verified = protocol.readFlash(address, AdditionalSettingsImage.SECTOR_SIZE);
                if (!Arrays.equals(expected, verified)) {
                    int diff = firstDiff(expected, verified);
                    throw new IOException("READ-BACK FAILED sector 0x"
                            + Integer.toHexString(sectorNo) + " at +0x" + Integer.toHexString(diff));
                }
                progress.onMessage("Read-back sector 0x" + Integer.toHexString(sectorNo)
                        + ": OK (4096/4096 bytes)");
            }
        } finally {
            protocol.closeProgrammingMode();
        }
        progress.onMessage("Все изменённые сектора подтверждены read-back сравнением.");
    }

    @Override public void reboot() throws IOException {
        protocol.reboot();
    }

    private static int firstDiff(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) if (a[i] != b[i]) return i;
        return n;
    }
}
