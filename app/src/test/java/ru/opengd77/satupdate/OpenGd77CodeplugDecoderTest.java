package ru.opengd77.satupdate;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class OpenGd77CodeplugDecoderTest {
    @Test public void decodesCoreCodeplugSections() {
        byte[] general = new byte[0x28];
        putAscii(general, 0, "R3TEST");
        putBcd8Be(general, 8, 2501234);

        byte[] scans = new byte[0x1640];
        scans[0] = 1;
        int scan = 0x40;
        putAscii(scans, scan, "LOCAL");
        ByteUtil.putU16le(scans, scan + 0x10, 1);
        ByteUtil.putU16le(scans, scan + 0x12, 129);

        byte[] bank0 = new byte[0x1c10];
        bank0[0] = 0x01;
        int ch0 = 0x10;
        putAscii(bank0, ch0, "VHF TEST");
        putBcd8Le(bank0, ch0 + 0x10, 14550000);
        putBcd8Le(bank0, ch0 + 0x14, 14550000);
        bank0[ch0 + 0x18] = 0;
        bank0[ch0 + 0x19] = 8;      // MD-9600 25 Вт
        bank0[ch0 + 0x26] = 0x40;   // NO_BEEP; Eco remains enabled
        bank0[ch0 + 0x33] = 0x02;

        byte[] banks = new byte[7 * 0x1c10];
        banks[0] = 0x01;
        int ch128 = 0x10;
        putAscii(banks, ch128, "DMR TEST");
        putBcd8Le(banks, ch128 + 0x10, 43850000);
        putBcd8Le(banks, ch128 + 0x14, 43090000);
        banks[ch128 + 0x18] = 1;
        banks[ch128 + 0x2B] = 1;
        banks[ch128 + 0x2C] = 3;
        ByteUtil.putU16le(banks, ch128 + 0x2E, 1);
        banks[ch128 + 0x31] = 0x40;

        byte[] zones = new byte[0xAC00];
        zones[0] = 0x01;
        int zone = 0x20;
        putAscii(zones, zone, "HOME");
        ByteUtil.putU16le(zones, zone + 0x10, 1);
        ByteUtil.putU16le(zones, zone + 0x12, 129);

        byte[] contacts = new byte[0x6000];
        putAscii(contacts, 0, "WORLD");
        putBcd8Be(contacts, 0x10, 91);
        contacts[0x14] = 0;
        contacts[0x17] = 0x02;

        byte[] rxGroups = new byte[0x1840];
        rxGroups[0] = 2;
        int group = 0x80;
        putAscii(rxGroups, group, "TG LIST");
        ByteUtil.putU16le(rxGroups, group + 0x10, 1);

        CodeplugModel model = OpenGd77CodeplugDecoder.decode(new CodeplugSnapshot(
                general, scans, bank0, zones, banks, contacts, rxGroups));

        assertEquals("R3TEST", model.general.radioName);
        assertEquals(2501234, model.general.dmrId);
        assertEquals(2, model.channels.size());
        assertEquals(1, model.channels.get(0).index);
        assertEquals(145500000L, model.channels.get(0).rxHz);
        assertEquals(8, model.channels.get(0).powerSetting);
        assertEquals("25 Вт", model.channels.get(0).powerText());
        assertFalse(model.channels.get(0).beepEnabled);
        assertTrue(model.channels.get(0).ecoEnabled);
        assertEquals(129, model.channels.get(1).index);
        assertTrue(model.channels.get(1).digital);
        assertEquals(3, model.channels.get(1).colorCode);
        assertEquals(2, model.channels.get(1).timeSlot);
        assertEquals(1, model.contacts.size());
        assertEquals(91, model.contacts.get(0).number);
        assertEquals(1, model.zones.size());
        assertEquals(2, model.zones.get(0).channelIndices.size());
        assertEquals(1, model.rxGroups.size());
        assertEquals(1, model.rxGroups.get(0).contactIndices.size());
        assertEquals(1, model.scanLists.size());
        assertEquals(2, model.scanLists.get(0).channelIndices.size());
    }

    @Test public void decodesRussianCp1251YaAndRealDmrId() {
        byte[] general = new byte[0x28];
        putCodeplug1251(general, 0, "RUS", 8);
        putBcd8Be(general, 8, 4010151);

        byte[] scans = new byte[0x1640];
        byte[] bank0 = new byte[0x1c10];
        bank0[0] = 0x01;
        int ch0 = 0x10;
        putCodeplug1251(bank0, ch0, "Моя станция", 16);
        putBcd8Le(bank0, ch0 + 0x10, 43850000);
        putBcd8Le(bank0, ch0 + 0x14, 43090000);

        byte[] banks = new byte[7 * 0x1c10];

        byte[] zones = new byte[0xAC00];
        zones[0] = 0x01;
        int zone = 0x20;
        putCodeplug1251(zones, zone, "Моя зона", 16);
        ByteUtil.putU16le(zones, zone + 0x10, 1);

        byte[] contacts = new byte[0x6000];
        putCodeplug1251(contacts, 0, "МОЯ ГРУППА", 16);
        putBcd8Be(contacts, 0x10, 4010151);

        byte[] rxGroups = new byte[0x1840];

        CodeplugModel model = OpenGd77CodeplugDecoder.decode(new CodeplugSnapshot(
                general, scans, bank0, zones, banks, contacts, rxGroups));

        assertEquals(4010151, model.general.dmrId);
        assertEquals("Моя станция", model.channels.get(0).name);
        assertEquals("Моя зона", model.zones.get(0).name);
        assertEquals("МОЯ ГРУППА", model.contacts.get(0).name);
    }

    @Test public void decodesNoEcoFlagIndependently() {
        byte[] general = new byte[0x28];
        byte[] scans = new byte[0x1640];
        byte[] bank0 = new byte[0x1c10];
        bank0[0] = 1;
        int ch = 0x10;
        putAscii(bank0, ch, "ECO TEST");
        bank0[ch + 0x26] = 0x20; // NO_ECO only

        CodeplugModel model = OpenGd77CodeplugDecoder.decode(new CodeplugSnapshot(
                general, scans, bank0, new byte[0xAC00], new byte[7 * 0x1c10],
                new byte[0x6000], new byte[0x1840]));

        assertTrue(model.channels.get(0).beepEnabled);
        assertFalse(model.channels.get(0).ecoEnabled);
    }

    private static void putAscii(byte[] b, int off, String s) {
        byte[] x = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(x, 0, b, off, x.length);
    }

    private static void putCodeplug1251(byte[] b, int off, String s, int maxLen) {
        byte[] x = s.getBytes(Charset.forName("windows-1251"));
        int n = Math.min(x.length, maxLen);
        for (int i = 0; i < n; i++) {
            int v = x[i] & 0xff;
            b[off + i] = (byte)(v == 0xff ? 0x7f : v);
        }
        for (int i = n; i < maxLen; i++) b[off + i] = (byte)0xff;
    }

    private static void putBcd8Le(byte[] b, int off, int value) {
        long packed = packBcd(value);
        ByteUtil.putU32le(b, off, packed);
    }

    private static void putBcd8Be(byte[] b, int off, int value) {
        long packed = packBcd(value);
        b[off] = (byte)((packed >>> 24) & 0xff);
        b[off + 1] = (byte)((packed >>> 16) & 0xff);
        b[off + 2] = (byte)((packed >>> 8) & 0xff);
        b[off + 3] = (byte)(packed & 0xff);
    }

    private static long packBcd(int value) {
        long out = 0;
        int shift = 0;
        int v = value;
        while (v > 0) {
            out |= ((long)(v % 10)) << shift;
            v /= 10;
            shift += 4;
        }
        return out;
    }
}
