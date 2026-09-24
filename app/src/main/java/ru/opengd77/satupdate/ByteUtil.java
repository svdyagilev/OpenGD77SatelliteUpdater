package ru.opengd77.satupdate;

final class ByteUtil {
    private ByteUtil() {}

    static int u16le(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8);
    }

    static long u32le(byte[] b, int o) {
        return ((long)b[o] & 0xffL)
                | (((long)b[o + 1] & 0xffL) << 8)
                | (((long)b[o + 2] & 0xffL) << 16)
                | (((long)b[o + 3] & 0xffL) << 24);
    }

    static void putU16le(byte[] b, int o, int v) {
        b[o] = (byte)(v & 0xff);
        b[o + 1] = (byte)((v >>> 8) & 0xff);
    }

    static void putU32le(byte[] b, int o, long v) {
        b[o] = (byte)(v & 0xff);
        b[o + 1] = (byte)((v >>> 8) & 0xff);
        b[o + 2] = (byte)((v >>> 16) & 0xff);
        b[o + 3] = (byte)((v >>> 24) & 0xff);
    }

    static void putU16be(byte[] b, int o, int v) {
        b[o] = (byte)((v >>> 8) & 0xff);
        b[o + 1] = (byte)(v & 0xff);
    }

    static void putU32be(byte[] b, int o, long v) {
        b[o] = (byte)((v >>> 24) & 0xff);
        b[o + 1] = (byte)((v >>> 16) & 0xff);
        b[o + 2] = (byte)((v >>> 8) & 0xff);
        b[o + 3] = (byte)(v & 0xff);
    }

    static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (int i = 0; i < data.length; i++) {
            if (i != 0) sb.append(' ');
            sb.append(String.format("%02X", data[i] & 0xff));
        }
        return sb.toString();
    }
}
