package ru.opengd77.satupdate;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class SatelliteCreationTest {
    // Reproduces the uploaded project's TLV layout without storing private radio data.
    private byte[] blank() {
        byte[] b = new byte[0x2000];
        Arrays.fill(b, (byte)0xff);
        System.arraycopy("OpenGD77".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 8);
        ByteUtil.putU32le(b, 8, 1);
        ByteUtil.putU32le(b, 12, 1); ByteUtil.putU32le(b, 16, 0x400);
        Arrays.fill(b, 20, 0x414, (byte)0x59);
        ByteUtil.putU32le(b, 0x414, 2); ByteUtil.putU32le(b, 0x418, 0x200);
        Arrays.fill(b, 0x41c, 0x61c, (byte)0x72);
        return b;
    }
    private byte[] candidate() {
        byte[] p = new byte[0x9d8];
        System.arraycopy("ISS".getBytes(StandardCharsets.US_ASCII), 0, p, 0, 3);
        TleEntry tle = new TleEntry("ISS", 25544,
                "1 25544U 98067A   26272.81986231  .00009634  00000+0  18116-3 0  9999",
                "2 25544  51.6318 170.3464 0004691 174.6338 185.4701 15.49258637587098");
        System.arraycopy(OpenGd77SatelliteEncoder.encodeOrbit40(tle), 0, p, 8, 40);
        ByteUtil.putU32le(p, 48, 145800000);
        ByteUtil.putU32le(p, 52, 145990000);
        ByteUtil.putU16le(p, 56, 670);
        p[76] = 'A';
        return p;
    }
    private void rejected(byte[] b) {
        byte[] copy = b.clone();
        try { UpdatePlan.build(b, candidate()); fail("Expected rejection"); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
        assertArrayEquals(copy, b);
    }
    @Test public void missingBankCreatesFullRecordsAndPreservesOtherBytes() {
        byte[] before = blank(), copy = before.clone(), payload = candidate();
        assertEquals(0, SatelliteBankInspector.inspect(before, 0).records.size());
        UpdatePlan p = UpdatePlan.build(before, payload);
        assertTrue(p.createsSatelliteBank);
        assertEquals(0, p.currentSatelliteCount); assertEquals(1, p.newSatelliteCount);
        assertEquals(0x2061c, p.satelliteAbsoluteHeaderAddress());
        assertEquals(Arrays.asList(0), p.changedSectorIndexes);
        assertArrayEquals(copy, before);
        assertArrayEquals(payload, Arrays.copyOfRange(p.afterImage, 0x624, 0xffc));
        for (int i = 0; i < before.length; i++)
            if (i < 0x61c || i >= 0xffc) assertEquals("Preserve +" + i, before[i], p.afterImage[i]);
        assertEquals(3, ByteUtil.u32le(p.afterImage, 0x61c));
        assertEquals(0x9d8, ByteUtil.u32le(p.afterImage, 0x620));
        assertEquals("ISS", SatelliteBankInspector.inspect(p.afterImage, 0).records.get(0).name);
        UpdatePlan again = UpdatePlan.build(p.afterImage, payload);
        assertFalse(again.createsSatelliteBank); assertEquals(0, again.changedBytes);
        byte[] changed = payload.clone(); changed[48]++;
        assertEquals(0, UpdatePlan.build(p.afterImage, changed).changedBytes);
    }
    @Test public void malformedChainIsNotTreatedAsMissingBank() {
        byte[] b=blank(); ByteUtil.putU32le(b, 16, 0x3000); rejected(b);
        b=blank(); ByteUtil.putU32le(b, 0x620, 2); rejected(b);
        b=blank(); ByteUtil.putU32le(b, 0x61c, 4); rejected(b);
        b=blank(); Arrays.fill(b, 0x61c, b.length, (byte)0); rejected(b);
    }
    @Test public void occupiedTailAndInsufficientSpaceAreRejected() {
        byte[] b=blank(); b[0x1fff]=0; rejected(b);
        b=blank(); ByteUtil.putU32le(b, 0x61c, 4); ByteUtil.putU32le(b, 0x620, 0x1700); rejected(b);
    }
    @Test public void unknownWellFormedBlockIsPreservedAndTwoSectorsCanBeUsed() {
        byte[] b=blank(); ByteUtil.putU32le(b, 0x61c, 42); ByteUtil.putU32le(b, 0x620, 0x400);
        Arrays.fill(b, 0x624, 0xa24, (byte)0x6a);
        UpdatePlan p=UpdatePlan.build(b, candidate());
        assertEquals(0x20a24, p.satelliteAbsoluteHeaderAddress());
        assertEquals(Arrays.asList(0,1), p.changedSectorIndexes);
        assertArrayEquals(Arrays.copyOf(b,0xa24), Arrays.copyOf(p.afterImage,0xa24));
        assertArrayEquals(Arrays.copyOfRange(b,0x1404,b.length),Arrays.copyOfRange(p.afterImage,0x1404,b.length));
    }
    @Test public void emptyOrInvalidCandidateDoesNotCreateBank() {
        byte[] b=blank();
        try { UpdatePlan.build(b,new byte[0x9d8]); fail(); } catch(IllegalArgumentException expected) { }
        byte[] p=candidate(); Arrays.fill(p,8,48,(byte)0xff);
        try { UpdatePlan.build(b,p); fail(); } catch(IllegalArgumentException expected) { }
    }
    @Test public void badHeaderAndUnsupportedExistingBankRemainProtected() {
        byte[] b=blank(); b[0]=0; rejected(b);
        b=blank(); ByteUtil.putU32le(b,8,2); rejected(b);
        b=blank(); ByteUtil.putU32le(b,0x61c,3); ByteUtil.putU32le(b,0x620,100); rejected(b);
    }
}
