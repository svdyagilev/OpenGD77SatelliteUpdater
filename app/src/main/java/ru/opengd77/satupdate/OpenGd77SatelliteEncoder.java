package ru.opengd77.satupdate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class OpenGd77SatelliteEncoder {
    static final int RECORD_SIZE = 100;
    static final int MAX_SATELLITES = 25;
    static final int SATELLITE_PAYLOAD_SIZE = 0x09D8;

    static final class BuildResult {
        final byte[] payload;
        final List<String> loaded;
        final List<String> missing;
        BuildResult(byte[] payload, List<String> loaded, List<String> missing) {
            this.payload = payload;
            this.loaded = loaded;
            this.missing = missing;
        }
    }

    private OpenGd77SatelliteEncoder() {}

    static BuildResult buildPayload(List<SatelliteConfig> configs, Map<Integer, TleEntry> tleByCatalog) {
        byte[] payload = new byte[SATELLITE_PAYLOAD_SIZE];
        List<String> loaded = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int index = 0;
        for (SatelliteConfig cfg : configs) {
            if (index >= MAX_SATELLITES) break;
            TleEntry tle = tleByCatalog.get(cfg.catalogNumber);
            if (tle == null) {
                missing.add(cfg.name + " / " + cfg.catalogNumber);
                continue;
            }
            byte[] record = encodeRecord(cfg, tle);
            System.arraycopy(record, 0, payload, index * RECORD_SIZE, RECORD_SIZE);
            loaded.add(cfg.name + " / " + cfg.catalogNumber + " epoch " + tle.epochText());
            index++;
        }
        // Last 20 bytes are zero in the observed CPS satellite TLV.
        return new BuildResult(payload, loaded, missing);
    }

    static byte[] encodeRecord(SatelliteConfig cfg, TleEntry tle) {
        byte[] out = new byte[RECORD_SIZE];
        putAscii(out, 0x00, 8, cfg.name);
        byte[] orbit = encodeOrbit40(tle);
        System.arraycopy(orbit, 0, out, 0x08, orbit.length);

        ByteUtil.putU32le(out, 0x30, mhzToCpsHz(cfg.rx1));
        ByteUtil.putU32le(out, 0x34, mhzToCpsHz(cfg.tx1));
        ByteUtil.putU16le(out, 0x38, toneToTenths(cfg.ctcss));
        ByteUtil.putU16le(out, 0x3A, toneToTenths(cfg.armCtcss));
        ByteUtil.putU32le(out, 0x3C, mhzToCpsHz(cfg.rx2));
        ByteUtil.putU32le(out, 0x40, mhzToCpsHz(cfg.tx2));
        ByteUtil.putU32le(out, 0x44, mhzToCpsHz(cfg.rx3));
        // 0x48..0x4B observed as zero. Tx3 exists in Satellites.txt but is not stored in this 100-byte record.
        putAscii(out, 0x4C, 24, cfg.aprsConfig);
        return out;
    }

    static byte[] encodeOrbit40(TleEntry tle) {
        StringBuilder chars = new StringBuilder(80);
        chars.append(TleParser.field(tle.line1, 18, 2));
        chars.append(TleParser.field(tle.line1, 20, 12));
        chars.append(TleParser.field(tle.line1, 33, 10));
        chars.append(TleParser.field(tle.line2, 8, 8));
        chars.append(TleParser.field(tle.line2, 17, 8));
        chars.append(TleParser.field(tle.line2, 26, 7));
        chars.append(TleParser.field(tle.line2, 34, 8));
        chars.append(TleParser.field(tle.line2, 43, 8));
        chars.append(TleParser.field(tle.line2, 52, 11));
        chars.append(TleParser.field(tle.line2, 63, 5));
        if (chars.length() != 79) throw new IllegalStateException("Unexpected TLE packed length " + chars.length());
        chars.append(' ');

        byte[] out = new byte[40];
        for (int i = 0; i < 40; i++) {
            int hi = charToNibble(chars.charAt(i * 2));
            int lo = charToNibble(chars.charAt(i * 2 + 1));
            out[i] = (byte)((hi << 4) | lo);
        }
        return out;
    }

    private static int charToNibble(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c == '.') return 0xA;
        if (c == ' ') return 0xB;
        if (c == '-') return 0xC;
        throw new IllegalArgumentException("Unsupported TLE character: '" + c + "'");
    }

    static long mhzToCpsHz(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        float mhz;
        try { mhz = Float.parseFloat(text.trim()); }
        catch (NumberFormatException e) { return 0; }
        return (long)((double)mhz * 1_000_000.0d);
    }

    static int toneToTenths(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        float tone;
        try { tone = Float.parseFloat(text.trim()); }
        catch (NumberFormatException e) { return 0; }
        return (int)((double)tone * 10.0d);
    }

    private static void putAscii(byte[] out, int offset, int max, String text) {
        if (text == null) return;
        byte[] raw = text.getBytes(StandardCharsets.US_ASCII);
        int n = Math.min(max, raw.length);
        System.arraycopy(raw, 0, out, offset, n);
    }
}
