package ru.opengd77.satupdate;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/** Read-only OpenGD77/MD-9600 codeplug decoder. No full-codeplug write path exists here. */
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
    private static final int CSS_NONE = 0xFFFF;
    private static final int CSS_DCS = 0x8000;
    private static final int CSS_DCS_INVERTED = 0x4000;
    private static final int CSS_DCS_MASK = CSS_DCS | CSS_DCS_INVERTED;
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final String DTMF_DIGITS = "0123456789ABCD*#";

    private OpenGd77CodeplugDecoder() {}

    static CodeplugModel decode(CodeplugSnapshot raw) {
        CodeplugModel.DeviceInfo deviceInfo = decodeDeviceInfo(raw.deviceInfo);
        CodeplugModel.General general = decodeGeneral(raw.generalSettings);
        CodeplugModel.BootInfo boot = decodeBoot(raw.bootAndVfos);
        CodeplugModel.DtmfSettings dtmfSettings = decodeDtmfSettings(raw.dtmfSettings);
        List<CodeplugModel.Channel> vfos = decodeVfos(raw.bootAndVfos);
        List<CodeplugModel.Channel> channels = decodeChannels(raw.channelBank0, raw.channelBanks1to7);
        List<CodeplugModel.Contact> contacts = decodeContacts(raw.contacts);
        List<CodeplugModel.DtmfContact> dtmfContacts = decodeDtmfContacts(raw.dtmfContacts);
        List<CodeplugModel.AprsConfig> aprs = decodeAprs(raw.aprsConfigs);
        List<CodeplugModel.Satellite> satellites = decodeSatellites(raw.additionalSettings);
        List<CodeplugModel.Zone> zones = decodeZones(raw.zones);
        List<CodeplugModel.RxGroup> rxGroups = decodeRxGroups(raw.rxGroups);
        List<CodeplugModel.ScanList> scanLists = decodeScanLists(raw.scanLists);
        return new CodeplugModel(deviceInfo, general, boot, dtmfSettings, vfos, channels,
                contacts, dtmfContacts, aprs, satellites, zones, rxGroups, scanLists,
                raw.totalBytes());
    }

    private static CodeplugModel.DeviceInfo decodeDeviceInfo(byte[] b) {
        if (b.length < 0x60) {
            return new CodeplugModel.DeviceInfo(0, 0, 0, 0, "", "", "", "", "", "");
        }
        return new CodeplugModel.DeviceInfo(
                ByteUtil.u16le(b, 0), ByteUtil.u16le(b, 2),
                ByteUtil.u16le(b, 4), ByteUtil.u16le(b, 6),
                codeplugText(b, 0x10, 8), codeplugText(b, 0x18, 16),
                codeplugText(b, 0x28, 8), codeplugText(b, 0x30, 8),
                codeplugText(b, 0x38, 8), codeplugText(b, 0x40, 24));
    }

    private static CodeplugModel.General decodeGeneral(byte[] b) {
        if (b.length < 12) throw new IllegalArgumentException("General settings block too short");
        String name = codeplugText(b, 0, 8);
        long dmrId = bcd8be(b, 8);
        int version = b.length > 12 ? b[12] & 0xff : 0;
        int vox = b.length > 19 ? b[19] & 0xff : 0;
        int f1 = b.length > 26 ? b[26] & 0xff : 0;
        int f2 = b.length > 27 ? b[27] & 0xff : 0;
        int f3 = b.length > 28 ? b[28] & 0xff : 0;
        int f4 = b.length > 29 ? b[29] & 0xff : 0;
        return new CodeplugModel.General(name, dmrId, version, vox, f1, f2, f3, f4);
    }

    private static CodeplugModel.BootInfo decodeBoot(byte[] b) {
        if (b.length < 0x48) {
            return new CodeplugModel.BootInfo(0, false, "", "", new ArrayList<>());
        }
        List<Integer> quick = new ArrayList<>();
        if (b.length >= 0x20) {
            for (int i = 0; i < 10; i++) quick.add(ByteUtil.u16le(b, 0x0C + i * 2));
        }
        return new CodeplugModel.BootInfo(b[0] & 0xff, (b[1] & 0xff) != 0,
                codeplugText(b, 0x28, 16), codeplugText(b, 0x38, 16), quick);
    }

    private static List<CodeplugModel.Channel> decodeVfos(byte[] b) {
        List<CodeplugModel.Channel> out = new ArrayList<>();
        if (b.length < 0xE8) return out;
        out.add(decodeChannelRecord(b, 0x78, 0, "VFO A"));
        out.add(decodeChannelRecord(b, 0xB0, 0, "VFO B"));
        return out;
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
                int index = bank * CHANNELS_PER_BANK + i + 1;
                int off = base + 0x10 + i * CHANNEL_SIZE;
                out.add(decodeChannelRecord(src, off, index, "CH " + index));
            }
        }
        return out;
    }

    private static CodeplugModel.Channel decodeChannelRecord(byte[] src, int off, int index, String fallbackName) {
        if (off < 0 || off + CHANNEL_SIZE > src.length) {
            throw new IllegalArgumentException("Channel record outside buffer");
        }
        String name = codeplugText(src, off, 16);
        if (name.isEmpty()) name = fallbackName;
        long rx = bcd8le(src, off + 0x10) * 10L;
        long tx = bcd8le(src, off + 0x14) * 10L;
        boolean digital = (src[off + 0x18] & 0xff) == 1;
        int powerSetting = src[off + 0x19] & 0xff;
        int totRaw = src[off + 0x1B] & 0xff;
        int totSeconds = totRaw <= 33 ? totRaw * 15 : 0;

        long latRaw = (src[off + 0x1A] & 0xffL)
                | ((src[off + 0x1C] & 0xffL) << 8)
                | ((src[off + 0x1D] & 0xffL) << 16);
        long lonRaw = (src[off + 0x1E] & 0xffL)
                | ((src[off + 0x1F] & 0xffL) << 8)
                | ((src[off + 0x24] & 0xffL) << 16);

        CodeplugModel.Tone rxTone = decodeTone(ByteUtil.u16le(src, off + 0x20));
        CodeplugModel.Tone txTone = decodeTone(ByteUtil.u16le(src, off + 0x22));
        int rusFlags = src[off + 0x25] & 0xff;
        int libreFlags = src[off + 0x26] & 0xff;
        boolean beepEnabled = (libreFlags & 0x40) == 0;
        boolean ecoEnabled = (libreFlags & 0x20) == 0;
        boolean useLocation = (libreFlags & 0x08) != 0;
        boolean forceDmo = (libreFlags & 0x04) != 0;
        boolean roaming = (libreFlags & 0x01) != 0;
        long optionalId = 0;
        if ((libreFlags & 0x80) != 0) {
            optionalId = ((long)(src[off + 0x27] & 0xff) << 16)
                    | ((long)(src[off + 0x28] & 0xff) << 8)
                    | (long)(src[off + 0x29] & 0xff);
        }

        int rxGroup = src[off + 0x2B] & 0xff;
        int cc = src[off + 0x2C] & 0xff;
        int aprsIndex = src[off + 0x2D] & 0xff;
        int contact = ByteUtil.u16le(src, off + 0x2E);
        int flag1 = src[off + 0x30] & 0xff;
        int flag2 = src[off + 0x31] & 0xff;
        int flag3 = src[off + 0x32] & 0xff;
        int flag4 = src[off + 0x33] & 0xff;
        int ts = (flag2 & 0x40) != 0 ? 2 : 1;
        int stepIndex = (src[off + 0x36] >>> 4) & 0x0f;
        int sql = src[off + 0x37] & 0xff;

        return new CodeplugModel.Channel(index, name, rx, tx, digital, cc, ts,
                contact, rxGroup, (flag4 & 0x04) != 0, (flag4 & 0x02) != 0,
                rxTone, txTone, powerSetting, beepEnabled, ecoEnabled,
                totSeconds, useLocation, fixed24ToDouble(latRaw), fixed24ToDouble(lonRaw),
                optionalId, forceDmo, roaming, (rusFlags & 0x80) != 0,
                (rusFlags & 0x40) != 0, aprsIndex, (flag4 & 0x40) != 0,
                (flag4 & 0x20) != 0, (flag4 & 0x10) != 0, (flag4 & 0x08) != 0,
                (flag4 & 0x01) != 0, sql, stepIndex, flag1 & 0x03,
                (flag1 >>> 2) & 0x03, (flag3 >>> 6) & 0x03,
                (flag3 >>> 5) & 0x01, (flag3 & 0x10) != 0,
                (flag3 >>> 2) & 0x03, (flag3 & 0x01) != 0,
                (flag2 >>> 7) & 0x01, (flag2 >>> 5) & 0x01,
                (flag2 >>> 4) & 0x01, (flag2 & 0x08) != 0,
                (flag2 & 0x04) != 0, (flag2 & 0x02) != 0,
                (flag2 & 0x01) != 0, rusFlags, libreFlags,
                flag1, flag2, flag3, flag4);
    }

    static CodeplugModel.Tone decodeTone(int raw) {
        raw &= 0xffff;
        if (raw == 0 || raw == CSS_NONE) {
            return new CodeplugModel.Tone(CodeplugModel.Tone.Type.NONE, 0, raw);
        }
        if ((raw & CSS_DCS_MASK) == 0) {
            int ctcssTenths = bcd16(raw);
            if (ctcssTenths < 0) {
                return new CodeplugModel.Tone(CodeplugModel.Tone.Type.UNKNOWN, 0, raw);
            }
            return new CodeplugModel.Tone(CodeplugModel.Tone.Type.CTCSS, ctcssTenths, raw);
        }
        int dcsCode = raw & ~CSS_DCS_MASK;
        CodeplugModel.Tone.Type type = (raw & CSS_DCS_INVERTED) != 0
                ? CodeplugModel.Tone.Type.DCS_INVERTED
                : CodeplugModel.Tone.Type.DCS_NORMAL;
        return new CodeplugModel.Tone(type, dcsCode, raw);
    }

    private static int bcd16(int raw) {
        int value = 0;
        int mul = 1;
        for (int i = 0; i < 4; i++) {
            int nibble = (raw >>> (i * 4)) & 0x0f;
            if (nibble > 9) return -1;
            value += nibble * mul;
            mul *= 10;
        }
        return value;
    }

    private static CodeplugModel.DtmfSettings decodeDtmfSettings(byte[] b) {
        if (b.length < 0x78) {
            return new CodeplugModel.DtmfSettings("", "", "", 0, 0, 0, 0,
                    false, 0, "", "", 0, 0, 0, 0, 0, 0, 0);
        }
        int flag1 = b[44] & 0xff;
        return new CodeplugModel.DtmfSettings(
                dtmfCode(b, 0, 8, true), dtmfCode(b, 8, 16, true),
                dtmfCode(b, 24, 16, true), b[40] & 0xff, b[41] & 0xff,
                b[42] & 0xff, b[43] & 0xff, (flag1 & 0x80) != 0,
                (flag1 >>> 5) & 0x03, dtmfCode(b, 48, 30, true),
                dtmfCode(b, 80, 30, true), (b[112] & 0xff) * 0.1,
                (b[113] & 0xff) * 0.1, (b[114] & 0xff) * 100,
                (b[115] & 0xff) * 100, (b[116] & 0xff) * 100,
                b[117] & 0xff, (b[118] & 0xff) * 100);
    }

    private static List<CodeplugModel.DtmfContact> decodeDtmfContacts(byte[] b) {
        List<CodeplugModel.DtmfContact> out = new ArrayList<>();
        if (b.length < 63 * 32) return out;
        for (int i = 0; i < 63; i++) {
            int off = i * 32;
            String name = codeplugText(b, off, 16);
            if (name.isEmpty()) continue;
            out.add(new CodeplugModel.DtmfContact(i + 1, name, dtmfCode(b, off + 16, 16, false)));
        }
        return out;
    }

    private static String dtmfCode(byte[] b, int off, int len, boolean allowBackspace) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < len && off + i < b.length; i++) {
            int v = b[off + i] & 0xff;
            if (v == 0xff) break;
            if (v < DTMF_DIGITS.length()) s.append(DTMF_DIGITS.charAt(v));
            else if (allowBackspace && v == 16) s.append('←');
            else break;
        }
        return s.toString();
    }

    private static List<CodeplugModel.AprsConfig> decodeAprs(byte[] b) {
        List<CodeplugModel.AprsConfig> out = new ArrayList<>();
        if (b.length < 8 * 64) return out;
        for (int i = 0; i < 8; i++) {
            int off = i * 64;
            String name = codeplugText(b, off, 8);
            int magic = ByteUtil.u16le(b, off + 62);
            if (name.isEmpty()) continue;
            long latRaw = (b[off + 9] & 0xffL) | ((b[off + 10] & 0xffL) << 8)
                    | ((b[off + 11] & 0xffL) << 16);
            long lonRaw = (b[off + 12] & 0xffL) | ((b[off + 13] & 0xffL) << 8)
                    | ((b[off + 14] & 0xffL) << 16);
            long txHz = ByteUtil.u32le(b, off + 55) * 10L;
            out.add(new CodeplugModel.AprsConfig(i + 1, name, b[off + 8] & 0xff,
                    fixed24ToDouble(latRaw), fixed24ToDouble(lonRaw),
                    codeplugText(b, off + 15, 6), b[off + 21] & 0xff,
                    codeplugText(b, off + 22, 6), b[off + 28] & 0xff,
                    b[off + 29] & 0xff, b[off + 30] & 0xff,
                    codeplugText(b, off + 31, 24), txHz, b[off + 61] & 0xff, magic));
        }
        return out;
    }

    private static List<CodeplugModel.Satellite> decodeSatellites(byte[] b) {
        List<CodeplugModel.Satellite> out = new ArrayList<>();
        if (b.length != AdditionalSettingsImage.READ_SIZE) return out;
        try {
            SatelliteBankInspector.Summary s = SatelliteBankInspector.inspect(b, System.currentTimeMillis());
            for (SatelliteBankInspector.RecordInfo r : s.records) {
                out.add(new CodeplugModel.Satellite(r.name, r.ageDays));
            }
        } catch (RuntimeException ignored) {
            // Deep viewer is best-effort; malformed optional custom data must not hide the main codeplug.
        }
        return out;
    }

    private static List<CodeplugModel.Contact> decodeContacts(byte[] b) {
        if (b.length != MAX_CONTACTS * CONTACT_SIZE) throw new IllegalArgumentException("Contacts length");
        List<CodeplugModel.Contact> out = new ArrayList<>();
        for (int i = 0; i < MAX_CONTACTS; i++) {
            int off = i * CONTACT_SIZE;
            String name = codeplugText(b, off, 16);
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
            String name = codeplugText(b, off, 16);
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
            String name = codeplugText(b, off, 16);
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
            String name = codeplugText(b, off, 15);
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

    private static double fixed24ToDouble(long raw) {
        boolean negative = (raw & 0x800000L) != 0;
        long value = raw & 0x7fffffL;
        double result = (value >>> 15) + ((value & 0x7fffL) / 10000.0);
        return negative ? -result : result;
    }

    private static int channelRef(int encoded) {
        return (encoded > 0 && encoded <= 1024) ? encoded : 0;
    }

    private static boolean bit(byte[] b, int byteOffset, int bit) {
        return ((b[byteOffset] >>> bit) & 1) != 0;
    }

    /** OpenGD77RUS stores labels as CP1251 and maps lowercase 'я' 0xFF to 0x7F. */
    static String codeplugText(byte[] b, int offset, int max) {
        if (offset < 0 || offset >= b.length || max <= 0) return "";
        int limit = Math.min(max, b.length - offset);
        int end = 0;
        while (end < limit) {
            int v = b[offset + end] & 0xff;
            if (v == 0 || v == 0xff) break;
            end++;
        }
        if (end == 0) return "";
        byte[] text = new byte[end];
        for (int i = 0; i < end; i++) {
            int v = b[offset + i] & 0xff;
            text[i] = (byte)(v == 0x7f ? 0xff : v);
        }
        return new String(text, WINDOWS_1251).trim();
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
