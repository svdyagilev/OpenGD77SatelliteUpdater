package ru.opengd77.satupdate;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

public class UpdatePlanTest {
    @Test public void satelliteTlvAtObservedOffsetChangesOnlySector20() {
        byte[] image = new byte[AdditionalSettingsImage.READ_SIZE];
        byte[] magic = "OpenGD77".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, image, 0, magic.length);
        ByteUtil.putU32le(image, 8, 1);

        int off = 12;
        ByteUtil.putU32le(image, off, 1); ByteUtil.putU32le(image, off + 4, 0x400); off += 8 + 0x400;
        ByteUtil.putU32le(image, off, 2); ByteUtil.putU32le(image, off + 4, 0x200); off += 8 + 0x200;
        assertEquals(0x61c, off);
        ByteUtil.putU32le(image, off, 3); ByteUtil.putU32le(image, off + 4, 0x09d8);

        byte[] payload = new byte[0x09d8];
        byte[] name = "ISS".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, payload, 0, name.length);
        Arrays.fill(payload, 8, 48, (byte)0x11);

        UpdatePlan plan = UpdatePlan.build(image, payload);
        assertEquals(0x2061c, plan.satelliteAbsoluteHeaderAddress());
        assertEquals(0x20624, plan.satelliteAbsolutePayloadStart());
        assertEquals(0x20ffb, plan.satelliteAbsolutePayloadEndInclusive());
        assertEquals(1, plan.newSatelliteCount);
        assertTrue(plan.changedBytes > 0);
        assertEquals(1, plan.changedSectorIndexes.size());
        assertEquals(Integer.valueOf(0), plan.changedSectorIndexes.get(0));
        assertFalse(Arrays.equals(plan.beforeImage, plan.afterImage));

        for (int i = 0x1000; i < 0x2000; i++) {
            assertEquals("sector 0x21 must remain untouched", plan.beforeImage[i], plan.afterImage[i]);
        }
    }
}
