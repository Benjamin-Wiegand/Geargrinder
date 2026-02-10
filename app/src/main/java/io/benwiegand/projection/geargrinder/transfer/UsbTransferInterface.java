package io.benwiegand.projection.geargrinder.transfer;

import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// uses USB bulk transfers to separate AA messages
// other implementations may need to leverage the 16-bit payload size in each message
public class UsbTransferInterface implements AATransferInterface {
    private final ParcelFileDescriptor pfd;
    private final InputStream is;
    private final OutputStream os;
    private final int maxTransferSize;

    private boolean alive = true;

    public UsbTransferInterface(ParcelFileDescriptor pfd, InputStream is, OutputStream os, int maxTransferSize) {
        this.pfd = pfd;
        this.is = is;
        this.os = os;
        this.maxTransferSize = maxTransferSize;
    }

    @Override
    public boolean alive() {
        return alive;
    }

    @Override
    public void sendFrame(byte[] buffer, int offset, int length) throws IOException {
        assert length <= maxTransferSize;
        try {
            os.write(buffer, offset, length);
        } catch (IOException e) {
            alive = false;
            throw e;
        }
    }

    @Override
    public int readFrame(byte[] buffer) throws IOException {
        try {
            int len = is.read(buffer);   // one usb bulk transfer per message
            if (len < 0) throw new IOException("stream closed (" + len + ")");
            return len;
        } catch (IOException e) {
            alive = false;
            throw e;
        }

    }

    public void close() throws IOException {
        try {
            pfd.close();
        } finally {
            alive = false;
        }
    }
}
