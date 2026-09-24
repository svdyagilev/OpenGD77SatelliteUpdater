package ru.opengd77.satupdate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class UpdatePlan {
    final byte[] beforeImage;
    final byte[] afterImage;
    final AdditionalSettingsImage.Tlv satelliteTlv;
    final int currentSatelliteCount;
    final int newSatelliteCount;
    final int changedBytes;
    final List<String> changedRecords;
    final List<Integer> changedSectorIndexes;

    private UpdatePlan(byte[] beforeImage,
                       byte[] afterImage,
                       AdditionalSettingsImage.Tlv satelliteTlv,
                       int currentSatelliteCount,
                       int newSatelliteCount,
                       int changedBytes,
                       List<String> changedRecords,
                       List<Integer> changedSectorIndexes) {
        this.beforeImage = beforeImage;
        this.afterImage = afterImage;
        this.satelliteTlv = satelliteTlv;
        this.currentSatelliteCount = currentSatelliteCount;
        this.newSatelliteCount = newSatelliteCount;
        this.changedBytes = changedBytes;
        this.changedRecords = changedRecords;
        this.changedSectorIndexes = changedSectorIndexes;
    }

    static UpdatePlan build(byte[] currentImage, byte[] newSatellitePayload) {
        if (currentImage == null || currentImage.length != AdditionalSettingsImage.READ_SIZE) {
            throw new IllegalArgumentException("Current image must be exactly 0x2000 bytes");
        }
        if (newSatellitePayload == null || newSatellitePayload.length != OpenGd77SatelliteEncoder.SATELLITE_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Satellite payload must be exactly 0x09D8 bytes");
        }

        byte[] before = Arrays.copyOf(currentImage, currentImage.length);
        AdditionalSettingsImage beforeParsed = new AdditionalSettingsImage(before);
        AdditionalSettingsImage.Tlv sat = beforeParsed.findTlv(AdditionalSettingsImage.SATELLITE_TLV_ID);
        if (sat == null) throw new IllegalStateException("Satellite TLV ID 3 not found");
        if (sat.payloadLength != OpenGd77SatelliteEncoder.SATELLITE_PAYLOAD_SIZE) {
            throw new IllegalStateException("Unexpected Satellite TLV length 0x" + Integer.toHexString(sat.payloadLength));
        }
        if (sat.payloadOffset < 0 || sat.payloadOffset + sat.payloadLength > before.length) {
            throw new IllegalStateException("Satellite TLV is outside protected read window");
        }

        AdditionalSettingsImage afterParsed = new AdditionalSettingsImage(before);
        afterParsed.replaceSatellitePayload(newSatellitePayload);
        byte[] after = afterParsed.bytes();

        int payloadStart = sat.payloadOffset;
        int payloadEnd = sat.payloadOffset + sat.payloadLength; // exclusive
        int changedBytes = 0;
        for (int i = 0; i < before.length; i++) {
            if (before[i] != after[i]) {
                changedBytes++;
                if (i < payloadStart || i >= payloadEnd) {
                    throw new IllegalStateException("Dry-run changed byte outside Satellite TLV at +0x" + Integer.toHexString(i));
                }
            }
        }

        byte[] currentPayload = Arrays.copyOfRange(before, payloadStart, payloadEnd);
        int currentCount = countRecords(currentPayload);
        int newCount = countRecords(newSatellitePayload);

        List<String> changedRecords = new ArrayList<>();
        for (int i = 0; i < OpenGd77SatelliteEncoder.MAX_SATELLITES; i++) {
            int off = i * OpenGd77SatelliteEncoder.RECORD_SIZE;
            byte[] oldRec = Arrays.copyOfRange(currentPayload, off, off + OpenGd77SatelliteEncoder.RECORD_SIZE);
            byte[] newRec = Arrays.copyOfRange(newSatellitePayload, off, off + OpenGd77SatelliteEncoder.RECORD_SIZE);
            if (Arrays.equals(oldRec, newRec)) continue;
            String oldName = recordName(oldRec);
            String newName = recordName(newRec);
            if (oldName.equals(newName) && !oldName.isEmpty()) changedRecords.add(oldName);
            else if (oldName.isEmpty() && !newName.isEmpty()) changedRecords.add("+ " + newName);
            else if (!oldName.isEmpty() && newName.isEmpty()) changedRecords.add("- " + oldName);
            else changedRecords.add(oldName + " -> " + newName);
        }

        List<Integer> changedSectors = new ArrayList<>();
        for (int s = 0; s < AdditionalSettingsImage.READ_SIZE / AdditionalSettingsImage.SECTOR_SIZE; s++) {
            int from = s * AdditionalSettingsImage.SECTOR_SIZE;
            int to = from + AdditionalSettingsImage.SECTOR_SIZE;
            if (!Arrays.equals(Arrays.copyOfRange(before, from, to), Arrays.copyOfRange(after, from, to))) {
                changedSectors.add(s);
            }
        }

        return new UpdatePlan(before, after, sat, currentCount, newCount, changedBytes,
                changedRecords, changedSectors);
    }

    int satelliteAbsoluteHeaderAddress() {
        return AdditionalSettingsImage.FLASH_BASE + satelliteTlv.headerOffset;
    }

    int satelliteAbsolutePayloadStart() {
        return AdditionalSettingsImage.FLASH_BASE + satelliteTlv.payloadOffset;
    }

    int satelliteAbsolutePayloadEndInclusive() {
        return satelliteAbsolutePayloadStart() + satelliteTlv.payloadLength - 1;
    }

    private static int countRecords(byte[] payload) {
        int count = 0;
        for (int i = 0; i < OpenGd77SatelliteEncoder.MAX_SATELLITES; i++) {
            int off = i * OpenGd77SatelliteEncoder.RECORD_SIZE;
            boolean nonzero = false;
            for (int j = 0; j < 8; j++) {
                if (payload[off + j] != 0) { nonzero = true; break; }
            }
            if (nonzero) count++;
        }
        return count;
    }

    private static String recordName(byte[] record) {
        int end = 0;
        while (end < 8 && record[end] != 0) end++;
        return new String(record, 0, end, StandardCharsets.US_ASCII).trim();
    }
}
