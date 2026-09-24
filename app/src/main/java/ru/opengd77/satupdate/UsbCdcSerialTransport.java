package ru.opengd77.satupdate;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

final class UsbCdcSerialTransport implements Closeable {
    static final int VID = 0x1FC9;
    static final int PID = 0x0094;

    private final UsbManager manager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbSerialPort port;
    private int readPacketSize = 64;

    private byte[] pending = new byte[0];
    private int pendingOffset = 0;

    UsbCdcSerialTransport(UsbManager manager) {
        this.manager = manager;
    }

    UsbDevice findDevice() {
        for (UsbDevice d : manager.getDeviceList().values()) {
            if (d.getVendorId() == VID && d.getProductId() == PID) return d;
        }
        return null;
    }

    String describeDevice(UsbDevice d) {
        StringBuilder b = new StringBuilder();
        b.append(String.format("USB %04X:%04X, interfaces=%d", d.getVendorId(), d.getProductId(), d.getInterfaceCount()));
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface intf = d.getInterface(i);
            b.append("\n  IF ").append(i)
                    .append(" id=").append(intf.getId())
                    .append(" class=0x").append(Integer.toHexString(intf.getInterfaceClass()))
                    .append(" sub=0x").append(Integer.toHexString(intf.getInterfaceSubclass()))
                    .append(" eps=").append(intf.getEndpointCount());
            for (int e = 0; e < intf.getEndpointCount(); e++) {
                UsbEndpoint ep = intf.getEndpoint(e);
                b.append("\n    EP 0x").append(Integer.toHexString(ep.getAddress()))
                        .append(" type=").append(ep.getType())
                        .append(" mps=").append(ep.getMaxPacketSize());
            }
        }
        return b.toString();
    }

    void open(UsbDevice d) throws IOException {
        close();
        device = d;
        CdcAcmSerialDriver driver = new CdcAcmSerialDriver(d);
        if (driver.getPorts().isEmpty()) throw new IOException("CDC ACM serial port not found");

        connection = manager.openDevice(d);
        if (connection == null) throw new IOException("Cannot open USB device (permission?)");

        port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(115200, UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            UsbEndpoint in = port.getReadEndpoint();
            if (in != null && in.getMaxPacketSize() > 0) readPacketSize = in.getMaxPacketSize();
            readPacketSize = Math.max(64, readPacketSize);
            pending = new byte[0];
            pendingOffset = 0;
        } catch (Exception e) {
            try { port.close(); } catch (Exception ignored) {}
            port = null;
            connection = null;
            throw e instanceof IOException ? (IOException)e : new IOException(e);
        }
    }

    synchronized void write(byte[] data, int timeoutMs) throws IOException {
        if (port == null) throw new IOException("USB serial port is not open");
        port.write(data, timeoutMs);
    }

    synchronized byte[] readExact(int count, int timeoutMs) throws IOException {
        if (port == null) throw new IOException("USB serial port is not open");
        byte[] out = new byte[count];
        int off = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (off < count) {
            int available = pending.length - pendingOffset;
            if (available > 0) {
                int take = Math.min(available, count - off);
                System.arraycopy(pending, pendingOffset, out, off, take);
                pendingOffset += take;
                off += take;
                if (pendingOffset >= pending.length) {
                    pending = new byte[0];
                    pendingOffset = 0;
                }
                continue;
            }

            int remainingMs = (int)Math.max(1, deadline - System.currentTimeMillis());
            int bufferSize = Math.max(readPacketSize, count - off);
            byte[] tmp = new byte[bufferSize];
            int n = port.read(tmp, remainingMs);
            if (n <= 0) throw new IOException("USB read timeout/error: got " + off + "/" + count);

            int take = Math.min(n, count - off);
            System.arraycopy(tmp, 0, out, off, take);
            off += take;

            if (n > take) {
                pending = Arrays.copyOfRange(tmp, take, n);
                pendingOffset = 0;
            }
        }
        return out;
    }

    @Override public void close() {
        if (port != null) {
            try { port.close(); } catch (Exception ignored) {}
        } else if (connection != null) {
            try { connection.close(); } catch (Exception ignored) {}
        }
        port = null;
        connection = null;
        device = null;
        pending = new byte[0];
        pendingOffset = 0;
    }
}
