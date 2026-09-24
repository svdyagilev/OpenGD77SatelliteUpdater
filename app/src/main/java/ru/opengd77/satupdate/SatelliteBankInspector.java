package ru.opengd77.satupdate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Read-only helpers for inspecting the satellite bank already stored in the radio. */
final class SatelliteBankInspector {
    static final class RecordInfo {
        final String name;
        final long epochMillis;
        final double ageDays;

        RecordInfo(String name, long epochMillis, double ageDays) {
            this.name = name;
            this.epochMillis = epochMillis;
            this.ageDays = ageDays;
        }

        String ageText() {
            if (epochMillis == Long.MIN_VALUE) return "epoch ?";
            return String.format(Locale.US, "%.1f d", ageDays);
        }
    }

    static final class Summary {
        final List<RecordInfo> records;
        final double newestAgeDays;
        final double oldestAgeDays;

        Summary(List<RecordInfo> records, double newestAgeDays, double oldestAgeDays) {
            this.records = records;
            this.newestAgeDays = newestAgeDays;
            this.oldestAgeDays = oldestAgeDays;
        }

        String compactText() {
            if (records.isEmpty()) return "0 спутников";
            if (Double.isNaN(newestAgeDays) || Double.isNaN(oldestAgeDays)) {
                return records.size() + " спутн., возраст Keps неизвестен";
            }
            if (Math.abs(oldestAgeDays - newestAgeDays) < 0.05d) {
                return String.format(Locale.US, "%d спутн., Keps %.1f d", records.size(), oldestAgeDays);
            }
            return String.format(Locale.US, "%d спутн., Keps %.1f–%.1f d",
                    records.size(), newestAgeDays, oldestAgeDays);
        }
    }

    private SatelliteBankInspector() {}

    static Summary inspect(byte[] image, long nowMillis) {
        AdditionalSettingsImage parsed = new AdditionalSettingsImage(image);
        AdditionalSettingsImage.Tlv sat = parsed.findTlv(AdditionalSettingsImage.SATELLITE_TLV_ID);
        if (sat == null || sat.payloadLength != OpenGd77SatelliteEncoder.SATELLITE_PAYLOAD_SIZE) {
            throw new IllegalStateException("Satellite TLV ID 3/0x09D8 not found");
        }

        List<RecordInfo> records = new ArrayList<>();
        double newest = Double.POSITIVE_INFINITY;
        double oldest = Double.NEGATIVE_INFINITY;
        boolean anyEpoch = false;

        byte[] data = parsed.bytes();
        for (int i = 0; i < OpenGd77SatelliteEncoder.MAX_SATELLITES; i++) {
            int rec = sat.payloadOffset + i * OpenGd77SatelliteEncoder.RECORD_SIZE;
            String name = recordName(data, rec);
            if (name.isEmpty()) continue;
            long epoch = decodeEpochMillis(data, rec + 0x08);
            double age = Double.NaN;
            if (epoch != Long.MIN_VALUE) {
                age = (nowMillis - epoch) / 86400000.0d;
                newest = Math.min(newest, age);
                oldest = Math.max(oldest, age);
                anyEpoch = true;
            }
            records.add(new RecordInfo(name, epoch, age));
        }

        return new Summary(records,
                anyEpoch ? newest : Double.NaN,
                anyEpoch ? oldest : Double.NaN);
    }

    static long decodeEpochMillis(byte[] bytes, int orbitOffset) {
        try {
            StringBuilder s = new StringBuilder(14);
            for (int i = 0; i < 7; i++) {
                int v = bytes[orbitOffset + i] & 0xff;
                s.append(nibbleToChar((v >>> 4) & 0x0f));
                s.append(nibbleToChar(v & 0x0f));
            }
            String yyText = s.substring(0, 2);
            String dayText = s.substring(2, 14).trim();
            if (!yyText.matches("[0-9]{2}") || dayText.isEmpty()) return Long.MIN_VALUE;
            int yy = Integer.parseInt(yyText);
            double day = Double.parseDouble(dayText);
            if (!(day >= 1.0d && day < 367.0d)) return Long.MIN_VALUE;
            int year = yy >= 57 ? 1900 + yy : 2000 + yy;

            GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            c.clear();
            c.set(Calendar.YEAR, year);
            c.set(Calendar.DAY_OF_YEAR, 1);
            return c.getTimeInMillis() + Math.round((day - 1.0d) * 86400000.0d);
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    private static char nibbleToChar(int n) {
        if (n >= 0 && n <= 9) return (char)('0' + n);
        if (n == 0xA) return '.';
        if (n == 0xB) return ' ';
        if (n == 0xC) return '-';
        throw new IllegalArgumentException("Unsupported packed TLE nibble " + n);
    }

    private static String recordName(byte[] data, int offset) {
        int end = 0;
        while (end < 8 && data[offset + end] != 0) end++;
        return new String(data, offset, end, StandardCharsets.US_ASCII).trim();
    }
}
