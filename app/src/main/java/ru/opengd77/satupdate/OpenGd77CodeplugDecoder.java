package ru.opengd77.satupdate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only decoder for the OpenGD77/MD-9600 codeplug blocks used by v0.5.
 * No encoder/write path intentionally exists here yet.
 */
final class OpenGd77CodeplugDecoder {
    private static final int CHANNEL_BANK_SIZE = 0x1C10;
    private static final int CHANNEL_SIZE = 0x38;
    private static final int CHANNELS_PER_BANK = 128;
    private static final int ZONE_SIZE = 0xB0;
    private static final int ZONE_BITMAP_SIZE = 0x20;
    private static final int MAX_ZONES = 250;
    private static final int CONTACT_SIZE = 0x18;
    private static final int MAX_CONTACTS = 1024;
    private static final int RX_GROUP_SIZE = 0x50;
    private static final int MAX_RX_GROUPS = 76;
    private static final int SCAN_LIST_SIZE = 0x58;
    private static final int SCAN_MAP_SIZE = 0x40;
    private static final int MAX_SCAN_LISTS = 64;

    private OpenGd77CodeplugDecoder() {}

    static CodeplugModel decode(CodeplugSnapshot raw) {
        CodeplugModel.General general = decodeGeneral(raw.generalSettings);
        List<CodeplugModel.Channel> channels = decodeChannels(raw.channelBank0, raw.channelBanks1to7);
        List<CodeplugModel.Contact> contacts = decodeContacts(raw.contacts);
        List<CodeplugModel.Zone> zones = decodeZones(raw.zones);
        List<CodeplugModel.RxGroup> rxGroups = decodeRxGroups(raw.rxGroups);
        List<CodeplugModel.ScanList> scanLists = decodeScanLists(raw.scanLists);
        return new CodeplugModel(general, channels, contacts, zones, rxGroups, scanLists, raw.totalBytes());
    }

    private static CodeplugModel.General decodeGeneral(byte[] b) {
        if (b.length < 12) throw new IllegalArgumentException("General settings block too short");
        String name = ascii(b, 0, 8);
        long dmrId = ByteUtil.u32le(b, 8);
        return new CodeplugModel.General(name, dmrId);
    }

    private static List<CodeplugModel.Channel> decodeChannels(byte[] bank0, byte[] banks1to7) {
        if (bank0.length != CHANNEL_BANK_SIZE) throw new IllegalArgumentException("Channel bank0 length");
        if (banks1to7.length != CHANNEL_BANK_SIZE * 7) throw new IllegalArgumentException("Channel flash banks length");
        List<CodeplugModel.Channel> out = new ArrayList<>();
        for (int bank = 0; bank < 8; bank++) {
            byte[] src = bank == 0 ? bank0 : banks1to7;
            int base = bank == 0 ? 0 : (bank - 1) * CHANNEL_BANK_SIZE;
            for (int i = 0; i < CHANNELS_PER_BANK; i++) {
                if (!bit(src, base + i / 8, i % 8)) continue;
                int off = base + 0x10 + i * CHANNEL_SIZE;
                String name = ascii(src, off, 16);
                if (name.isEmpty()) name = "CH " + (bank * CHANNELS_PER_BANK + i + 1);
                long rx = bcd8le(src, off + 0x10) * 10L;
                long tx = bcd8le(src, off + 0x14) * 10L;
                boolean digital = (src[off + 0x18] & 0xff) == 1;
                int rxGroup = src[off + 0x2B] & 0xff;
                int cc = src[off + 0x2C] & 0xff;
                int contact = ByteUtil.u16le(src, off + 0x2E);
                int ts = (src[off + 0x31] & 0x40) != 0 ? 2 : 1;
                int flag4 = src[off + 0x33] & 0xff;
                boolean rxOnly = (flag4 & 0x04) != 0;
                boolean wide25 = (flag4 & 0x02) != 0;
                out.add(new CodeplugModel.Channel(bank * CHANNELS_PER_BANK + i + 1,
                        name, rx, tx, digital, cc, ts, contact, rxGroup, rxOnly, wide25));
            }
        }
        return out;
    }

    private static List<CodeplugModel.Contact> decodeContacts(byte[] b) {
        if (b.length != MAX_CONTACTS * CONTACT_SIZE) throw new IllegalArgumentException("Contacts length");
        List<CodeplugModel.Contact> out = new ArrayList<>();
        for (int i = 0; i < MAX_CONTACTS; i++) {
            int off = i * CONTACT_SIZE;
            String name = ascii(b, off, 16);
            if (name.isEmpty()) continue;
            long number = bcd8be(b, off + 0x10);
            int type = b[off + 0x14] & 0xff;
            int tsOverride = b[off + 0x17] & 0x03;
            out.add(new CodeplugModel.Contact(i + 1, name, number, type, tsOverride));
        }
        return out;
    }

    private static List<CodeplugModel.Zone> decodeZones(byte[] b) {
        int expected = ZONE_BITMAP_SIZE + MAX_ZONES * ZONE_SIZE;
        if (b.length != expected) throw new IllegalArgumentException("Zones length 0x" + Integer.toHexString(b.length));
        List<CodeplugModel.Zone> out = new ArrayList<>();
        for (int i = 0; i < MAX_ZONES; i++) {
            if (!bit(b, i / 8, i % 8)) continue;
            int off = ZONE_BITMAP_SIZE + i * ZONE_SIZE;
            String name = ascii(b, off, 16);
            if (name.isEmpty()) continue;
            List<Integer> members = new ArrayList<>();
            for (int n = 0; n < 80; n++) {
                int idx = ByteUtil.u16le(b, off + 0x10 + n * 2);
                if (idx > 0 && idx <= 1024) members.add(idx);
            }
            out.add(new CodeplugModel.Zone(i + 1, name, members));
        }
        return out;
    }

    private static List<CodeplugModel.RxGroup> decodeRxGroups(byte[] b) {
        int expected = 0x80 + MAX_RX_GROUPS * RX_GROUP_SIZE;
        if (b.length != expected) throw new IllegalArgumentException("RX Groups length");
        List<CodeplugModel.RxGroup> out = new ArrayList<>();
        for (int i = 0; i < MAX_RX_GROUPS; i++) {
            int encodedLen = b[i] & 0xff;
            if (encodedLen == 0) continue;
            int count = Math.min(32, Math.max(0, encodedLen - 1));
            int off = 0x80 + i * RX_GROUP_SIZE;
            String name = ascii(b, off, 16);
            if (name.isEmpty()) continue;
            List<Integer> contacts = new ArrayList<>();
            for (int n = 0; n < count; n++) {
                int idx = ByteUtil.u16le(b, off + 0x10 + n * 2);
                if (idx > 0 && idx <= 1024) contacts.add(idx);
            }
            out.add(new CodeplugModel.RxGroup(i + 1, name, contacts));
        }
        return out;
    }

    private static List<CodeplugModel.ScanList> decodeScanLists(byte[] b) {
        int expected = SCAN_MAP_SIZE + MAX_SCAN_LISTS * SCAN_LIST_SIZE;
        if (b.length != expected) throw new IllegalArgumentException("Scan lists length");
        List<CodeplugModel.ScanList> out = new ArrayList<>();
        for (int i = 0; i < MAX_SCAN_LISTS; i++) {
            if ((b[i] & 0xff) == 0) continue;
            int off = SCAN_MAP_SIZE + i * SCAN_LIST_SIZE;
            String name = ascii(b, off, 15);
            if (name.isEmpty()) continue;
            List<Integer> members = new ArrayList<>();
            for (int n = 0; n < 32; n++) {
                int idx = ByteUtil.u16le(b, off + 0x10 + n * 2);
                if (idx > 0 && idx <= 1024) members.add(idx);
            }
            int primary = channelRef(ByteUtil.u16le(b, off + 0x50));
            int secondary = channelRef(ByteUtil.u16le(b, off + 0x52));
            int revert = channelRef(ByteUtil.u16le(b, off + 0x54));
            out.add(new CodeplugModel.ScanList(i + 1, name, members, primary, secondary, revert));
        }
        return out;
    }

    private static int channelRef(int encoded) {
        return (encoded > 0 && encoded <= 1024) ? encoded : 0;
    }

    private static boolean bit(byte[] b, int byteOffset, int bit) {
        return ((b[byteOffset] >>> bit) & 1) != 0;
    }

    private static String ascii(byte[] b, int offset, int max) {
        int end = 0;
        while (end < max) {
            int v = b[offset + end] & 0xff;
            if (v == 0 || v == 0xff) break;
            end++;
        }
        return new String(b, offset, end, StandardCharsets.ISO_8859_1).trim();
    }

    private static long bcd8le(byte[] b, int o) {
        long raw = ByteUtil.u32le(b, o);
        return bcdFromPacked(raw);
    }

    private static long bcd8be(byte[] b, int o) {
        long raw = ((long)(b[o] & 0xff) << 24)
                | ((long)(b[o + 1] & 0xff) << 16)
                | ((long)(b[o + 2] & 0xff) << 8)
                | (long)(b[o + 3] & 0xff);
        return bcdFromPacked(raw);
    }

    private static long bcdFromPacked(long raw) {
        long value = 0;
        long mul = 1;
        for (int i = 0; i < 8; i++) {
            int nibble = (int)((raw >>> (i * 4)) & 0x0f);
            if (nibble > 9) return 0;
            value += nibble * mul;
            mul *= 10;
        }
        return value;
    }
}
