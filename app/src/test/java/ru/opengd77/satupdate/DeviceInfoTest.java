package ru.opengd77.satupdate;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class DeviceInfoTest {
    @Test public void bandLimitsArePackedBcdWholeMhzNotBinaryOrTenths() {
        byte[] bytes = new byte[0x60];
        // Bytes as written by CPS DeviceInfo.MinFreq = ushort.Parse("400", HexNumber).
        bytes[0] = 0x00; bytes[1] = 0x04;
        bytes[2] = (byte)0x80; bytes[3] = 0x04;
        bytes[4] = 0x36; bytes[5] = 0x01;
        bytes[6] = 0x74; bytes[7] = 0x01;
        CodeplugModel.DeviceInfo d = OpenGd77CodeplugDecoder.decodeDeviceInfo(bytes, null);
        assertEquals(400, d.minUhf);
        assertEquals(480, d.maxUhf);
        assertEquals(136, d.minVhf);
        assertEquals(174, d.maxVhf);
        assertEquals("400–480 МГц", d.uhfRangeText());
        assertEquals("136–174 МГц", d.vhfRangeText());
    }

    @Test public void erasedInvalidAndReversedLimitsAreNotPresentedAsFrequencies() {
        byte[] bytes = new byte[0x60];
        Arrays.fill(bytes, (byte)0xff);
        assertEquals(-1, OpenGd77CodeplugDecoder.bandLimitMhz(bytes, 0));
        bytes[0] = 0; bytes[1] = 0;
        assertEquals(-1, OpenGd77CodeplugDecoder.bandLimitMhz(bytes, 0));
        bytes[0] = 0x0a; bytes[1] = 0x04;
        assertEquals(-1, OpenGd77CodeplugDecoder.bandLimitMhz(bytes, 0));
        bytes[0] = (byte)0x80; bytes[1] = 0x04;
        bytes[2] = 0; bytes[3] = 0x04;
        assertEquals("нет корректных данных в codeplug",
                OpenGd77CodeplugDecoder.decodeDeviceInfo(bytes, null).uhfRangeText());
    }

    @Test public void liveIdentitySurvivesEmptyLegacyDeviceInfo() {
        RadioDriver.Identity identity = new RadioDriver.Identity("MD-9600", 5, 1, "R20260920");
        for (byte[] bytes : new byte[][] {new byte[0], new byte[0x60], erasedBlock()}) {
            CodeplugModel.DeviceInfo d = OpenGd77CodeplugDecoder.decodeDeviceInfo(bytes, identity);
            assertEquals("MD-9600", d.liveModel);
            assertEquals("R20260920", d.liveFirmware);
            assertEquals("", d.hardwareVersion);
            assertEquals("", d.firmwareVersion);
        }
    }

    @Test public void liveFirmwareIsSeparateFromPotentiallyStaleCodeplugField() {
        byte[] bytes = new byte[0x60];
        byte[] old = "OLD-FW".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(old, 0, bytes, 0x38, old.length);
        CodeplugModel.DeviceInfo d = OpenGd77CodeplugDecoder.decodeDeviceInfo(bytes,
                new RadioDriver.Identity("MD-9600", 5, 1, "CURRENT-FW"));
        assertEquals("CURRENT-FW", d.liveFirmware);
        assertEquals("OLD-FW", d.firmwareVersion);
        assertEquals("", OpenGd77CodeplugDecoder.decodeDeviceInfo(bytes, null).liveFirmware);
    }

    private static byte[] erasedBlock() {
        byte[] bytes = new byte[0x60];
        Arrays.fill(bytes, (byte)0xff);
        return bytes;
    }
}
