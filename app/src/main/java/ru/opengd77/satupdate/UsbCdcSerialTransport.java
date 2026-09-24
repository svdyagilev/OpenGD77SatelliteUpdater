package ru.opengd77.satupdate;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.Closeable;
import java.io.IOException;

final class UsbCdcSerialTransport implements Closeable {
    static final int VID = 0x1FC9;
    static final int PID = 0x0094;

    private final UsbManager manager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface controlInterface;
    private UsbInterface dataInterface;
    private UsbEndpoint bulkIn;
    private UsbEndpoint bulkOut;

    UsbCdcSerialTransport(UsbManager manager) {
        this.manager = manager;
    }

    UsbDevice findDevice() {
        for (UsbDevice d : manager.getDeviceList().values()) {
            if (d.getVendorId() == VID && d.getProductId() == PID) return d;
        }
        return null;
    }

    void open(UsbDevice d) throws IOException {
        close();
        device = d;
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface intf = d.getInterface(i);
            if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_COMM) controlInterface = intf;
            if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA) dataInterface = intf;
        }
        if (dataInterface == null) {
            for (int i = 0; i < d.getInterfaceCount(); i++) {
                UsbInterface intf = d.getInterface(i);
                UsbEndpoint in = null, out = null;
                for (int e = 0; e < intf.getEndpointCount(); e++) {
                    UsbEndpoint ep = intf.getEndpoint(e);
                    if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.getDirection() == UsbConstants.USB_DIR_IN) in = ep;
                        else out = ep;
                    }
                }
                if (in != null && out != null) { dataInterface = intf; break; }
            }
        }
        if (dataInterface == null) throw new IOException("CDC data interface not found");

        connection = manager.openDevice(d);
        if (connection == null) throw new IOException("Cannot open USB device (permission?)");
        if (controlInterface != null && !connection.claimInterface(controlInterface, true))
            throw new IOException("Cannot claim CDC control interface");
        if (!connection.claimInterface(dataInterface, true))
            throw new IOException("Cannot claim CDC data interface");

        for (int e = 0; e < dataInterface.getEndpointCount(); e++) {
            UsbEndpoint ep = dataInterface.getEndpoint(e);
            if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
            if (ep.getDirection() == UsbConstants.USB_DIR_IN) bulkIn = ep;
            else bulkOut = ep;
        }
        if (bulkIn == null || bulkOut == null) throw new IOException("Bulk endpoints not found");

        if (controlInterface != null) configure1152008N1();
    }

    private void configure1152008N1() throws IOException {
        byte[] lc = new byte[] {0x00, (byte)0xC2, 0x01, 0x00, 0x00, 0x00, 0x08};
        int index = controlInterface.getId();
        int r = connection.controlTransfer(0x21, 0x20, 0, index, lc, lc.length, 1000);
        if (r < 0) throw new IOException("SET_LINE_CODING failed");
        connection.controlTransfer(0x21, 0x22, 0x03, index, null, 0, 1000);
    }

    synchronized void write(byte[] data, int timeoutMs) throws IOException {
        int off = 0;
        while (off < data.length) {
            int n = connection.bulkTransfer(bulkOut, data, off, data.length - off, timeoutMs);
            if (n <= 0) throw new IOException("USB write timeout/error at " + off);
            off += n;
        }
    }

    synchronized byte[] readExact(int count, int timeoutMs) throws IOException {
        byte[] out = new byte[count];
        int off = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (off < count) {
            int remaining = (int)Math.max(1, deadline - System.currentTimeMillis());
            int n = connection.bulkTransfer(bulkIn, out, off, count - off, remaining);
            if (n <= 0) throw new IOException("USB read timeout/error: got " + off + "/" + count);
            off += n;
        }
        return out;
    }

    @Override public void close() {
        if (connection != null) {
            try { if (dataInterface != null) connection.releaseInterface(dataInterface); } catch (Exception ignored) {}
            try { if (controlInterface != null) connection.releaseInterface(controlInterface); } catch (Exception ignored) {}
            connection.close();
        }
        connection = null;
        device = null;
        controlInterface = null;
        dataInterface = null;
        bulkIn = bulkOut = null;
    }
}
