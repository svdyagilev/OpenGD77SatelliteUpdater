package ru.opengd77.satupdate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class UpdatePlan {
    private static final int ORBIT_OFFSET = 0x08;
    private static final int ORBIT_LENGTH = 40;

    final byte[] beforeImage;
    final byte[] afterImage;
    final AdditionalSettingsImage.Tlv satelliteTlv;
    final int currentSatelliteCount;
    final int newSatelliteCount;
    final int changedBytes;
    final List<String> changedRecords;
    final List<String> skippedOlderRecords;
    final List<Integer> changedSectorIndexes;

    private UpdatePlan(byte[] beforeImage,
                       byte[] afterImage,
                       AdditionalSettingsImage.Tlv satelliteTlv,
                       int currentSatelliteCount,
                       int newSatelliteCount,
                       int changedBytes,
                       List<String> changedRecords,
                       List<String> skippedOlderRecords,
                       List<Integer> changedSectorIndexes) {
        this.beforeImage = beforeImage;
        this.afterImage = afterImage;
        this.satelliteTlv = satelliteTlv;
        this.currentSatelliteCount = currentSatelliteCount;
        this.newSatelliteCount = newSatelliteCount;
        this.changedBytes = changedBytes;
        this.changedRecords = changedRecords;
        this.skippedOlderRecords = skippedOlderRecords;
        this.changedSectorIndexes = changedSectorIndexes;
    }

    static UpdatePlan build(byte[] currentImage, byte[] candidateSatellitePayload) {
        if (currentImage == null || currentImage.length != AdditionalSettingsImage.READ_SIZE) {
            throw new IllegalArgumentException("Current image must be exactly 0x2000 bytes");
        }
        if (candidateSatellitePayload == null || candidateSatellitePayload.length != OpenGd77SatelliteEncoder.SATELLITE_PAYLOAD_SIZE) {
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

        int payloadStart = sat.payloadOffset;
        int payloadEnd = sat.payloadOffset + sat.payloadLength;
        byte[] currentPayload = Arrays.copyOfRange(before, payloadStart, payloadEnd);
        byte[] safePayload = Arrays.copyOf(currentPayload, currentPayload.length);
        List<String> skippedOlder = new ArrayList<>();

        // Safety rule: preserve the exact satellite bank already present in the radio.
        // Candidate records are used only as a source for bytes 0x08..0x2F (40-byte orbital data).
        // Name, order, frequencies, tones, APRS data, record count and the 20-byte tail stay untouched.
        for (int i = 0; i < OpenGd77SatelliteEncoder.MAX_SATELLITES; i++) {
            int currentOff = i * OpenGd77SatelliteEncoder.RECORD_SIZE;
            String currentName = recordName(currentPayload, currentOff);
            if (currentName.isEmpty()) continue;

            int candidateOff = findRecordOffsetByName(candidateSatellitePayload, currentName);
            if (candidateOff < 0) continue;

            // Never replace a valid epoch already in the radio with an older one.
            long currentEpoch = SatelliteBankInspector.decodeEpochMillis(currentPayload, currentOff + ORBIT_OFFSET);
            long candidateEpoch = SatelliteBankInspector.decodeEpochMillis(candidateSatellitePayload, candidateOff + ORBIT_OFFSET);
            if (currentEpoch != Long.MIN_VALUE && candidateEpoch != Long.MIN_VALUE && candidateEpoch < currentEpoch) {
                skippedOlder.add(currentName);
                continue;
            }

            System.arraycopy(candidateSatellitePayload, candidateOff + ORBIT_OFFSET,
                    safePayload, currentOff + ORBIT_OFFSET, ORBIT_LENGTH);
        }

        AdditionalSettingsImage afterParsed = new AdditionalSettingsImage(before);
        afterParsed.replaceSatellitePayload(safePayload);
        byte[] after = afterParsed.bytes();

        int changedBytes = 0;
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            changedBytes++;
            if (i < payloadStart || i >= payloadEnd) {
                throw new IllegalStateException("Dry-run changed byte outside Satellite TLV at +0x" + Integer.toHexString(i));
            }
            int rel = i - payloadStart;
            if (!isOrbitalByte(rel)) {
                throw new IllegalStateException("Dry-run attempted to change non-orbital satellite byte at payload +0x"
                        + Integer.toHexString(rel));
            }
        }

        int currentCount = countRecords(currentPayload);
        int newCount = countRecords(safePayload);
        if (newCount != currentCount) {
            throw new IllegalStateException("Satellite record count changed unexpectedly");
        }

        List<String> changedRecords = new ArrayList<>();
        for (int i = 0; i < OpenGd77SatelliteEncoder.MAX_SATELLITES; i++) {
            int off = i * OpenGd77SatelliteEncoder.RECORD_SIZE;
            byte[] oldRec = Arrays.copyOfRange(currentPayload, off, off + OpenGd77SatelliteEncoder.RECORD_SIZE);
            byte[] newRec = Arrays.copyOfRange(safePayload, off, off + OpenGd77SatelliteEncoder.RECORD_SIZE);
            if (Arrays.equals(oldRec, newRec)) continue;
            String oldName = recordName(oldRec, 0);
            String newName = recordName(newRec, 0);
            if (!oldName.equals(newName)) {
                throw new IllegalStateException("Satellite name/order changed unexpectedly: " + oldName + " -> " + newName);
            }
            changedRecords.add(oldName);
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
                changedRecords, skippedOlder, changedSectors);
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

    private static boolean isOrbitalByte(int payloadRelativeOffset) {
        int recordsBytes = OpenGd77SatelliteEncoder.MAX_SATELLITES * OpenGd77SatelliteEncoder.RECORD_SIZE;
        if (payloadRelativeOffset < 0 || payloadRelativeOffset >= recordsBytes) return false;
        int withinRecord = payloadRelativeOffset % OpenGd77SatelliteEncoder.RECORD_SIZE;
        return withinRecord >= ORBIT_OFFSET && withinRecord < ORBIT_OFFSET + ORBIT_LENGTH;
    }

    private static int findRecordOffsetByName(byte[] payload, String wantedName) {
        for (int i = 0; i < OpenGd77SatelliteEncoder.MAX_SATELLITES; i++) {
            int off = i * OpenGd77SatelliteEncoder.RECORD_SIZE;
            if (wantedName.equals(recordName(payload, off))) return off;
        }
        return -1;
    }

    private static int countRecords(byte[] payload) {
        int count = 0;
        for (int i = 0; i < OpenGd77SatelliteEncoder.MAX_SATELLITES; i++) {
            int off = i * OpenGd77SatelliteEncoder.RECORD_SIZE;
            if (!recordName(payload, off).isEmpty()) count++;
        }
        return count;
    }

    private static String recordName(byte[] payload, int offset) {
        int end = 0;
        while (end < 8 && payload[offset + end] != 0) end++;
        return new String(payload, offset, end, StandardCharsets.US_ASCII).trim();
    }
}
