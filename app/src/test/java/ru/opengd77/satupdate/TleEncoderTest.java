package ru.opengd77.satupdate;

import org.junit.Test;
import static org.junit.Assert.*;

public class TleEncoderTest {
    @Test public void issOrbitMatchesCps_2026_09_24() {
        String l1 = "1 25544U 98067A   26267.14191496  .00009634  00000+0  18116-3 0  9999";
        String l2 = "2 25544  51.6318 170.3464 0004691 174.6338 185.4701 15.49258637587098";
        TleEntry tle = new TleEntry("ISS (ZARYA)", 25544, l1, l2);
        byte[] got = OpenGd77SatelliteEncoder.encodeOrbit40(tle);
        byte[] expected = hex("26 26 7A 14 19 14 96 BA 00 00 96 34 B5 1A 63 18 17 0A 34 64 00 04 69 11 74 A6 33 81 85 A4 70 11 5A 49 25 86 37 58 70 9B");
        assertArrayEquals(expected, got);
    }

    @Test public void issFullRecordMatchesObservedCpsRecord() {
        SatelliteConfig c = new SatelliteConfig(25544, "ISS", "437.800", "145.990", "67", "0",
                "145.825", "145.825", "145.800", "0", "RS0ISS");
        TleEntry tle = new TleEntry("ISS (ZARYA)", 25544,
                "1 25544U 98067A   26267.14191496  .00009634  00000+0  18116-3 0  9999",
                "2 25544  51.6318 170.3464 0004691 174.6338 185.4701 15.49258637587098");
        byte[] expected = hex(
                "49 53 53 00 00 00 00 00 " +
                "26 26 7A 14 19 14 96 BA 00 00 96 34 B5 1A 63 18 17 0A 34 64 00 04 69 11 74 A6 33 81 85 A4 70 11 5A 49 25 86 37 58 70 9B " +
                "33 4C 18 1A 75 A1 B3 08 9E 02 00 00 E4 1C B1 08 E4 1C B1 08 43 BB B0 08 00 00 00 00 " +
                "52 53 30 49 53 53 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");
        assertArrayEquals(expected, OpenGd77SatelliteEncoder.encodeRecord(c, tle));
    }

    @Test public void cpsFloatFrequencyBehaviorIsReproduced() {
        assertEquals(437799987L, OpenGd77SatelliteEncoder.mhzToCpsHz("437.800"));
        assertEquals(145990005L, OpenGd77SatelliteEncoder.mhzToCpsHz("145.990"));
        assertEquals(145824996L, OpenGd77SatelliteEncoder.mhzToCpsHz("145.825"));
    }

    private static byte[] hex(String s) {
        String[] p = s.trim().split("\\s+");
        byte[] b = new byte[p.length];
        for (int i=0;i<p.length;i++) b[i]=(byte)Integer.parseInt(p[i],16);
        return b;
    }
}
