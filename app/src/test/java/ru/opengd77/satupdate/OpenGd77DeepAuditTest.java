package ru.opengd77.satupdate;

import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.*;

public class OpenGd77DeepAuditTest {
    @Test public void decodesExtendedChannelAndAuxiliaryBlocks() {
        byte[] device = new byte[0x60];
        ByteUtil.putU16le(device, 0, 0x0400);
        ByteUtil.putU16le(device, 2, 0x0480);
        ByteUtil.putU16le(device, 4, 0x0136);
        ByteUtil.putU16le(device, 6, 0x0174);
        putText(device, 0x10, "MD9600", 8);
        putText(device, 0x18, "SN123", 16);
        putText(device, 0x28, "CPS1", 8);
        putText(device, 0x30, "HW1", 8);
        putText(device, 0x38, "FW1", 8);
        putText(device, 0x40, "DSP1", 24);

        byte[] general = new byte[0x28];
        putText(general, 0, "R3ABC", 8);
        putBcd8Be(general, 8, 4010151);
        general[12] = 7;
        general[19] = 4;

        byte[] dtmf = new byte[0x78];
        putDtmf(dtmf, 0, "1234", 8);
        putDtmf(dtmf, 48, "12#", 30);
        putDtmf(dtmf, 80, "34*", 30);
        dtmf[43] = 10;
        dtmf[44] = (byte)0xA0;
        dtmf[112] = 50;
        dtmf[113] = 25;
        dtmf[114] = 2;
        dtmf[115] = 1;
        dtmf[116] = 2;
        dtmf[117] = 5;
        dtmf[118] = 3;

        byte[] aprs = new byte[8 * 64];
        putText(aprs, 0, "APRS1", 8);
        aprs[8] = 7;
        putFixed24(aprs, 9, 43.2500);
        putFixed24(aprs, 12, 76.9500);
        putText(aprs, 15, "WIDE1", 6);
        aprs[21] = 1;
        putText(aprs, 22, "WIDE2", 6);
        aprs[28] = 1;
        aprs[29] = 0;
        aprs[30] = 15;
        putText(aprs, 31, "Test APRS", 24);
        ByteUtil.putU32le(aprs, 55, 14580000L);
        aprs[61] = 0x06;
        ByteUtil.putU16le(aprs, 62, 0x4153);

        byte[] scans = new byte[0x1640];
        byte[] dtmfContacts = new byte[63 * 32];
        putText(dtmfContacts, 0, "DTMF TEST", 16);
        dtmfContacts[16] = 1;
        dtmfContacts[17] = 2;
        dtmfContacts[18] = 3;
        for (int i = 19; i < 32; i++) dtmfContacts[i] = (byte)0xff;

        byte[] bank0 = new byte[0x1c10];
        bank0[0] = 1;
        int ch = 0x10;
        putText(bank0, ch, "FULL TEST", 16);
        putBcd8Le(bank0, ch + 0x10, 43850000);
        putBcd8Le(bank0, ch + 0x14, 43090000);
        bank0[ch + 0x18] = 1;
        bank0[ch + 0x19] = 8;
        putChannelLatLon(bank0, ch, 43.2500, 76.9500);
        bank0[ch + 0x1B] = 4;
        bank0[ch + 0x25] = (byte)0xC0;
        bank0[ch + 0x26] = (byte)0xED;
        bank0[ch + 0x27] = 0x12;
        bank0[ch + 0x28] = 0x34;
        bank0[ch + 0x29] = 0x56;
        bank0[ch + 0x2B] = 1;
        bank0[ch + 0x2C] = 3;
        bank0[ch + 0x2D] = 1;
        ByteUtil.putU16le(bank0, ch + 0x2E, 1);
        bank0[ch + 0x30] = 0x09;
        bank0[ch + 0x31] = (byte)0xFF;
        bank0[ch + 0x32] = (byte)0xF9;
        bank0[ch + 0x33] = 0x7F;
        bank0[ch + 0x36] = 0x40;
        bank0[ch + 0x37] = 10;

        byte[] boot = new byte[0xE8];
        boot[0] = 1;
        boot[1] = 1;
        ByteUtil.putU16le(boot, 0x0C, 0x1234);
        putText(boot, 0x28, "HELLO", 16);
        putText(boot, 0x38, "RADIO", 16);
        int vfoA = 0x78;
        putText(boot, vfoA, "VFO A", 16);
        putBcd8Le(boot, vfoA + 0x10, 14550000);
        putBcd8Le(boot, vfoA + 0x14, 14550000);
        int vfoB = 0xB0;
        putText(boot, vfoB, "VFO B", 16);
        putBcd8Le(boot, vfoB + 0x10, 43350000);
        putBcd8Le(boot, vfoB + 0x14, 43350000);

        CodeplugModel model = OpenGd77CodeplugDecoder.decode(new CodeplugSnapshot(
                device, general, dtmf, aprs, scans, dtmfContacts, bank0, boot,
                new byte[0xAC00], new byte[7 * 0x1c10], new byte[0x6000],
                new byte[0x1840], new byte[0]));

        assertEquals("MD9600", model.deviceInfo.model);
        assertEquals(4010151, model.general.dmrId);
        assertEquals(7, model.general.codeplugVersion);
        assertEquals("HELLO", model.boot.line1);
        assertEquals(0x1234, (int)model.boot.quickKeys.get(0));
        assertEquals(2, model.vfos.size());
        assertEquals(145500000L, model.vfos.get(0).rxHz);

        CodeplugModel.Channel c = model.channels.get(0);
        assertEquals(60, c.totSeconds);
        assertTrue(c.useLocation);
        assertEquals(43.25, c.latitude, 0.0001);
        assertEquals(76.95, c.longitude, 0.0001);
        assertEquals(0x123456, c.optionalDmrId);
        assertTrue(c.forceDmo);
        assertTrue(c.roaming);
        assertTrue(c.fastCall);
        assertTrue(c.priority);
        assertFalse(c.beepEnabled);
        assertFalse(c.ecoEnabled);
        assertTrue(c.vox);
        assertTrue(c.zoneSkip);
        assertTrue(c.allSkip);
        assertTrue(c.allowTalkaround);
        assertEquals(10, c.squelchLevel);
        assertEquals("45%", c.squelchText());
        assertEquals(4, c.stepIndex);
        assertEquals("12.5 кГц", c.stepText());
        assertEquals(1, c.taTxTs1);
        assertEquals(2, c.taTxTs2);

        assertEquals(1, model.aprsConfigs.size());
        assertEquals(145800000L, model.aprsConfigs.get(0).txHz);
        assertEquals(1, model.dtmfContacts.size());
        assertEquals("123", model.dtmfContacts.get(0).code);
        assertEquals("1234", model.dtmfSettings.selfId);
        assertEquals("12#", model.dtmfSettings.pttUp);
        assertEquals(5.0, model.dtmfSettings.responseHoldSeconds, 0.001);
    }

    private static void putText(byte[] b, int off, String s, int max) {
        byte[] x = s.getBytes(Charset.forName("windows-1251"));
        int n = Math.min(x.length, max);
        for (int i = 0; i < n; i++) {
            int v = x[i] & 0xff;
            b[off + i] = (byte)(v == 0xff ? 0x7f : v);
        }
        for (int i = n; i < max; i++) b[off + i] = (byte)0xff;
    }

    private static void putDtmf(byte[] b, int off, String s, int max) {
        String chars = "0123456789ABCD*#";
        for (int i = 0; i < max; i++) b[off + i] = (byte)0xff;
        for (int i = 0; i < s.length() && i < max; i++) b[off + i] = (byte)chars.indexOf(s.charAt(i));
    }

    private static void putChannelLatLon(byte[] b, int off, double lat, double lon) {
        long la = fixed24(lat);
        long lo = fixed24(lon);
        b[off + 0x1A] = (byte)(la & 0xff);
        b[off + 0x1C] = (byte)((la >>> 8) & 0xff);
        b[off + 0x1D] = (byte)((la >>> 16) & 0xff);
        b[off + 0x1E] = (byte)(lo & 0xff);
        b[off + 0x1F] = (byte)((lo >>> 8) & 0xff);
        b[off + 0x24] = (byte)((lo >>> 16) & 0xff);
    }

    private static void putFixed24(byte[] b, int off, double value) {
        long v = fixed24(value);
        b[off] = (byte)(v & 0xff);
        b[off + 1] = (byte)((v >>> 8) & 0xff);
        b[off + 2] = (byte)((v >>> 16) & 0xff);
    }

    private static long fixed24(double value) {
        boolean neg = value < 0;
        double a = Math.abs(value);
        int integer = (int)a;
        int decimal = (int)Math.round((a - integer) * 10000.0);
        long v = ((long)integer << 15) | (decimal & 0x7fffL);
        return neg ? v | 0x800000L : v;
    }

    private static void putBcd8Le(byte[] b, int off, int value) {
        ByteUtil.putU32le(b, off, packBcd(value));
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
