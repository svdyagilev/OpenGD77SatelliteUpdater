package ru.opengd77.satupdate;

import java.io.IOException;
import java.util.Arrays;

final class OpenGd77Protocol {
    private static final int BLOCK = 32;
    private static final int SECTOR = 4096;
    private static final int TIMEOUT = 3000;
    private final UsbCdcSerialTransport io;

    OpenGd77Protocol(UsbCdcSerialTransport io) { this.io = io; }

    static final class FirmwareInfo {
        final long structVersion;
        final long radioType;
        final String fwRevision;
        FirmwareInfo(long v, long type, String rev) { structVersion = v; radioType = type; fwRevision = rev; }
    }

    FirmwareInfo readFirmwareInfo() throws IOException {
        byte[] data = readRaw(0x09, 0, 46);
        long ver = ByteUtil.u32le(data, 0);
        long type = ByteUtil.u32le(data, 4);
        String rev = asciiZ(data, 8, 16);
        return new FirmwareInfo(ver, type, rev);
    }

    byte[] readFlash(int address, int length) throws IOException {
        byte[] out = new byte[length];
        for (int off = 0; off < length; off += BLOCK) {
            int n = Math.min(BLOCK, length - off);
            byte[] part = readRaw(0x01, address + off, n);
            System.arraycopy(part, 0, out, off, n);
        }
        return out;
    }

    private byte[] readRaw(int command, int address, int length) throws IOException {
        byte[] req = new byte[8];
        req[0] = 'R';
        req[1] = (byte)command;
        ByteUtil.putU32be(req, 2, address & 0xffffffffL);
        ByteUtil.putU16be(req, 6, length);
        io.write(req, TIMEOUT);

        byte[] hdr = io.readExact(3, TIMEOUT);
        if (hdr[0] != 'R') throw new IOException("Read failed, response type=" + (char)hdr[0]);
        int got = ((hdr[1] & 0xff) << 8) | (hdr[2] & 0xff);
        if (got != length) throw new IOException("Read length " + got + " != " + length);
        return io.readExact(got, TIMEOUT);
    }

    void enterProgrammingMode(boolean writing) throws IOException {
        sendCommandFrame(0x00, 0, 0, 0, 0, 0, null); // Show CPS screen
        sendCommandFrame(0x01, 0, 0, 0, 0, 0, null); // Clear screen
        display(0, 0, "SatUpdate");
        display(0, 16, writing ? "Writing Keps" : "Reading Keps");
        sendCommandFrame(0x03, 0, 0, 0, 0, 0, null); // Render
        sendControl(writing ? 4 : 3); // red/green LED
        sendControl(2);               // save settings and VFOs, no reboot
    }

    void closeProgrammingMode() throws IOException {
        sendCommandFrame(0x05, 0, 0, 0, 0, 0, null);
    }

    void reboot() throws IOException {
        sendControl(1);
    }

    private void display(int x, int y, String text) throws IOException {
        byte[] msg = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        sendCommandFrame(0x02, x, y, 3, 0, 0, msg);
    }

    private void sendControl(int option) throws IOException {
        sendCommandFrame(0x06, option, 0, 0, 0, 0, null);
    }

    private void sendCommandFrame(int command, int xOrOption, int y, int font,
                                  int alignment, int inverted, byte[] message) throws IOException {
        byte[] req = new byte[23];
        req[0] = 'C';
        req[1] = (byte)command;
        req[2] = (byte)xOrOption;
        req[3] = (byte)y;
        req[4] = (byte)font;
        req[5] = (byte)alignment;
        req[6] = (byte)inverted;
        if (message != null) System.arraycopy(message, 0, req, 7, Math.min(16, message.length));
        io.write(req, TIMEOUT);
        byte[] ack = io.readExact(1, TIMEOUT);
        if (ack[0] != '-') throw new IOException("Command ACK expected '-', got 0x" + Integer.toHexString(ack[0] & 0xff));
    }

    void writeFlashSector(int sectorAddress, byte[] sectorData) throws IOException {
        if ((sectorAddress & (SECTOR - 1)) != 0) throw new IllegalArgumentException("Sector address not aligned");
        if (sectorData.length != SECTOR) throw new IllegalArgumentException("Sector must be 4096 bytes");

        int sectorNo = sectorAddress / SECTOR;
        byte[] select = new byte[] {'X', 0x01,
                (byte)((sectorNo >>> 16) & 0xff),
                (byte)((sectorNo >>> 8) & 0xff),
                (byte)(sectorNo & 0xff)};
        writeXAck(select, 0x01);

        for (int off = 0; off < SECTOR; off += BLOCK) {
            byte[] req = new byte[8 + BLOCK];
            req[0] = 'X';
            req[1] = 0x02;
            ByteUtil.putU32be(req, 2, (sectorAddress + off) & 0xffffffffL);
            ByteUtil.putU16be(req, 6, BLOCK);
            System.arraycopy(sectorData, off, req, 8, BLOCK);
            writeXAck(req, 0x02);
        }

        writeXAck(new byte[] {'X', 0x03}, 0x03);
    }

    private void writeXAck(byte[] req, int command) throws IOException {
        io.write(req, TIMEOUT);
        byte[] ack = io.readExact(2, TIMEOUT);
        if (ack[0] != 'X' || (ack[1] & 0xff) != command) {
            throw new IOException("X command 0x" + Integer.toHexString(command) + " failed: " + ByteUtil.hex(ack));
        }
    }

    private static String asciiZ(byte[] b, int o, int n) {
        int end = o;
        while (end < o + n && b[end] != 0) end++;
        return new String(Arrays.copyOfRange(b, o, end), java.nio.charset.StandardCharsets.US_ASCII);
    }
}
