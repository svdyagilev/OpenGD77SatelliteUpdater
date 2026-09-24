package ru.opengd77.satupdate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class AdditionalSettingsImage {
    static final int FLASH_BASE = 0x00020000;
    static final int READ_SIZE = 0x2000; // two complete 4K sectors for safe read-modify-write
    static final int SECTOR_SIZE = 0x1000;
    static final long SATELLITE_TLV_ID = 3;

    static final class Tlv {
        final int headerOffset;
        final int payloadOffset;
        final int payloadLength;
        Tlv(int h, int p, int l) { headerOffset = h; payloadOffset = p; payloadLength = l; }
    }

    private final byte[] data;

    AdditionalSettingsImage(byte[] twoSectors) {
        if (twoSectors.length != READ_SIZE) throw new IllegalArgumentException("Need exactly 0x2000 bytes");
        this.data = Arrays.copyOf(twoSectors, twoSectors.length);
        verifyHeader();
    }

    private void verifyHeader() {
        String magic = new String(data, 0, 8, StandardCharsets.US_ASCII);
        if (!"OpenGD77".equals(magic)) throw new IllegalArgumentException("OpenGD77 additional-settings magic not found");
        long version = ByteUtil.u32le(data, 8);
        if (version != 1) throw new IllegalArgumentException("Unsupported additional-settings version: " + version);
    }

    Tlv findTlv(long id) {
        int off = 12;
        // Known used length is 0x11A0, but allow the two-sector read while validating lengths.
        while (off + 8 <= data.length) {
            long blockId = ByteUtil.u32le(data, off);
            long lenLong = ByteUtil.u32le(data, off + 4);
            if (blockId == 0xffffffffL || lenLong == 0xffffffffL) break;
            if (lenLong < 0 || lenLong > data.length - off - 8) break;
            int len = (int)lenLong;
            if (blockId == id) return new Tlv(off, off + 8, len);
            off += 8 + len;
        }
        return null;
    }

    void replaceSatellitePayload(byte[] payload) {
        Tlv tlv = findTlv(SATELLITE_TLV_ID);
        if (tlv == null) throw new IllegalStateException("Satellite TLV ID 3 not found");
        if (tlv.payloadLength != payload.length) {
            throw new IllegalStateException("Satellite TLV length 0x" + Integer.toHexString(tlv.payloadLength)
                    + " != expected 0x" + Integer.toHexString(payload.length));
        }
        System.arraycopy(payload, 0, data, tlv.payloadOffset, payload.length);
    }

    byte[] bytes() { return Arrays.copyOf(data, data.length); }

    byte[] sector(int sectorIndex) {
        return Arrays.copyOfRange(data, sectorIndex * SECTOR_SIZE, (sectorIndex + 1) * SECTOR_SIZE);
    }
}
