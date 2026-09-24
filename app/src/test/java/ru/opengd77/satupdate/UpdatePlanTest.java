package ru.opengd77.satupdate;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

public class UpdatePlanTest {
    @Test public void orbitOnlyUpdatePreservesBankAndChangesOnlySector20() {
        byte[] image = makeImageWithSatellite("ISS");
        int payloadStart = 0x624;
        Arrays.fill(image, payloadStart + 8, payloadStart + 48, (byte)0x11);
        Arrays.fill(image, payloadStart + 48, payloadStart + 100, (byte)0x55);

        // Candidate payload intentionally inserts IO-86 before ISS and changes ISS metadata.
        // UpdatePlan must ignore IO-86, match ISS by name, and copy only ISS orbital bytes.
        byte[] candidate = new byte[0x09d8];
        byte[] io86 = "IO-86".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(io86, 0, candidate, 0, io86.length);
        Arrays.fill(candidate, 8, 48, (byte)0x33);
        Arrays.fill(candidate, 48, 100, (byte)0x44);

        byte[] iss = "ISS".getBytes(StandardCharsets.US_ASCII);
        int issCandidate = 100;
        System.arraycopy(iss, 0, candidate, issCandidate, iss.length);
        Arrays.fill(candidate, issCandidate + 8, issCandidate + 48, (byte)0x22);
        Arrays.fill(candidate, issCandidate + 48, issCandidate + 100, (byte)0x66);

        UpdatePlan plan = UpdatePlan.build(image, candidate);
        assertEquals(0x2061c, plan.satelliteAbsoluteHeaderAddress());
        assertEquals(0x20624, plan.satelliteAbsolutePayloadStart());
        assertEquals(0x20ffb, plan.satelliteAbsolutePayloadEndInclusive());
        assertEquals(1, plan.currentSatelliteCount);
        assertEquals(1, plan.newSatelliteCount);
        assertEquals(1, plan.changedRecords.size());
        assertEquals("ISS", plan.changedRecords.get(0));
        assertTrue(plan.skippedOlderRecords.isEmpty());
        assertEquals(40, plan.changedBytes);
        assertEquals(1, plan.changedSectorIndexes.size());
        assertEquals(Integer.valueOf(0), plan.changedSectorIndexes.get(0));

        for (int i = 0; i < 8; i++) assertEquals(image[payloadStart + i], plan.afterImage[payloadStart + i]);
        for (int i = 48; i < 100; i++) assertEquals(image[payloadStart + i], plan.afterImage[payloadStart + i]);
        for (int i = 8; i < 48; i++) assertEquals((byte)0x22, plan.afterImage[payloadStart + i]);

        for (int i = payloadStart + 100; i < payloadStart + 200; i++) assertEquals(0, plan.afterImage[i]);
        for (int i = 0x1000; i < 0x2000; i++) {
            assertEquals("sector 0x21 must remain untouched", plan.beforeImage[i], plan.afterImage[i]);
        }
    }

    @Test public void olderCandidateNeverDowngradesRadioEpoch() {
        byte[] image = makeImageWithSatellite("ISS");
        int payloadStart = 0x624;

        TleEntry newer = new TleEntry("ISS", 25544,
                "1 25544U 98067A   26267.14191496  .00009634  00000+0  18116-3 0  9999",
                "2 25544  51.6318 170.3464 0004691 174.6338 185.4701 15.49258637587098");
        TleEntry older = new TleEntry("ISS", 25544,
                "1 25544U 98067A   26239.53072227  .00008959  00000+0  16923-3 0  9994",
                "2 25544  51.6325 306.9725 0007720  89.1136 271.0737 15.49656235582819");

        byte[] newerOrbit = OpenGd77SatelliteEncoder.encodeOrbit40(newer);
        System.arraycopy(newerOrbit, 0, image, payloadStart + 8, 40);

        byte[] candidate = new byte[0x09d8];
        System.arraycopy("ISS".getBytes(StandardCharsets.US_ASCII), 0, candidate, 0, 3);
        byte[] olderOrbit = OpenGd77SatelliteEncoder.encodeOrbit40(older);
        System.arraycopy(olderOrbit, 0, candidate, 8, 40);

        UpdatePlan plan = UpdatePlan.build(image, candidate);
        assertEquals(0, plan.changedBytes);
        assertTrue(plan.changedRecords.isEmpty());
        assertEquals(1, plan.skippedOlderRecords.size());
        assertEquals("ISS", plan.skippedOlderRecords.get(0));
        assertArrayEquals(image, plan.afterImage);
    }

    private static byte[] makeImageWithSatellite(String name) {
        byte[] image = new byte[AdditionalSettingsImage.READ_SIZE];
        byte[] magic = "OpenGD77".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, image, 0, magic.length);
        ByteUtil.putU32le(image, 8, 1);

        int off = 12;
        ByteUtil.putU32le(image, off, 1); ByteUtil.putU32le(image, off + 4, 0x400); off += 8 + 0x400;
        ByteUtil.putU32le(image, off, 2); ByteUtil.putU32le(image, off + 4, 0x200); off += 8 + 0x200;
        assertEquals(0x61c, off);
        ByteUtil.putU32le(image, off, 3); ByteUtil.putU32le(image, off + 4, 0x09d8);

        int payloadStart = off + 8;
        byte[] n = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(n, 0, image, payloadStart, Math.min(8, n.length));
        return image;
    }
}
