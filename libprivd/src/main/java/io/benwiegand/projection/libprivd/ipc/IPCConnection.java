package io.benwiegand.projection.libprivd.ipc;

import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.benwiegand.projection.libprivd.sec.Sec;
import io.benwiegand.projection.libprivd.sec.SecAdapter;

public abstract class IPCConnection {
    private final String TAG = getTag();

    private static final int AUTH_TIMEOUT = 1000;

    private final Map<Integer, SecAdapter<Reply>> pendingReplyMap = new ConcurrentHashMap<>();
    private final Queue<IPCMessage> outgoingMessageQueue = new ConcurrentLinkedQueue<>();

    private final Object writeLock = new Object();
    private final Thread readThread;
    private final Thread writeThread;

    private final Socket socket;
    private final InputStream is;
    private final OutputStream os;

    private final CountDownLatch initLatch = new CountDownLatch(1);

    private final byte[] readBuffer = new byte[IPCMessage.HEADER_LENGTH + 65535];
    private final byte[] writeBuffer = new byte[IPCMessage.HEADER_LENGTH + 65535];

    private final byte[] tokenA;
    private final byte[] tokenB;

    private int msgIdCounter = 0;
    private boolean dead = false;

    public IPCConnection(Socket socket, byte[] tokenA, byte[] tokenB) throws IOException {
        this.readThread = new Thread(this::connectionLoop, "geargrinder-ipc-read");
        this.writeThread = new Thread(this::writeLoop, "geargrinder-ipc-write");
        this.socket = socket;
        is = socket.getInputStream();
        os = socket.getOutputStream();

        this.tokenA = tokenA;
        this.tokenB = tokenB;

        readThread.start();
        writeThread.start();
    }

    public void close() {
        if (dead) return;

        try {
            if (!socket.isClosed())
                socket.close();
        } catch (IOException e) {
            Log.e(TAG, "IOException while closing socket", e);
        }

        dead = true;
        readThread.interrupt();
        writeThread.interrupt();

        while (!pendingReplyMap.isEmpty()) {
            Set<Integer> messageIds = new HashSet<>(pendingReplyMap.keySet());
            for (int msgId : messageIds) {
                SecAdapter<Reply> adapter = pendingReplyMap.remove(msgId);
                assert adapter != null;
                adapter.throwError(new IOException("connection closed"));
            }
        }

        onClose();
    }

    public static final class Reply {
        public final int status;
        public final byte[] data;
        public final int offset;
        public final int length;

        public Reply(int status, byte[] data, int offset, int length) {
            this.status = status;
            this.data = data;
            this.offset = offset;
            this.length = length;
        }

        public Reply(int status, byte[] data) {
            this(status, data, 0, data.length);
        }

        public Reply(int status) {
            this(status, new byte[0]);
        }

        public static Reply copyFromMessage(IPCMessage msg) {
            assert msg.isReply();
            byte[] data = new byte[msg.getDataLength()];
            System.arraycopy(msg.getBuffer(), IPCMessage.HEADER_LENGTH, data, 0, data.length);
            return new Reply(msg.getCommand(), data);
        }
    }

    protected abstract String getTag();

    protected void onInitComplete(boolean success) {

    }

    protected abstract Reply onCommand(int command, byte[] data, int offset, int length);

    protected abstract void onClose();

    public void waitForInit(long timeout) throws InterruptedException, TimeoutException {
        if (!initLatch.await(timeout, TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("timed out waiting for IPC init");
        }
    }

    public boolean isAlive() {
        return !dead && initLatch.getCount() != 1 && socket.isConnected();
    }

    public Sec<Reply> send(int command, byte[] data, int offset, int length) {
        if (dead) return Sec.premeditatedError(new IOException("connection closed"));
        SecAdapter.SecWithAdapter<Reply> secWithAdapter = SecAdapter.createThreadless();

        synchronized (outgoingMessageQueue) {
            int msgId = nextMsgId();
            pendingReplyMap.put(msgId, secWithAdapter.secAdapter());

            try {
                outgoingMessageQueue.add(new IPCMessage(new byte[IPCMessage.HEADER_LENGTH + length])
                        .clear()
                        .setMessageId(msgId)
                        .setCommand(command)
                        .copyData(data, offset, length));
                outgoingMessageQueue.notify();
            } catch (Throwable t) {
                pendingReplyMap.remove(msgId);
                return Sec.premeditatedError(t);
            }
        }

        return secWithAdapter.sec();
    }

    public Sec<Reply> send(int command, byte[] data) {
        return send(command, data, 0, data.length);
    }

    public Sec<Reply> send(int command, Serializable serializable) {
        byte[] data;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {

            oos.writeObject(serializable);
            oos.flush();
            data = baos.toByteArray();

        } catch (IOException e) {
            return Sec.premeditatedError(e);
        }

        return send(command, data);
    }

    public Sec<Reply> send(int command) {
        return send(command, new byte[0]);
    }

    private int nextMsgId() {
        if (msgIdCounter++ >= 0xffff) msgIdCounter = 0;
        return msgIdCounter;
    }

    private void readAll(InputStream is, byte[] buffer, int offset, int length) throws IOException {
        int len, i = 0;
        while (i < length) {
            len = is.read(buffer, i + offset, length - i);
            if (len < 0) throw new IOException("EOS (" + len + ")");
            i += len;
        }
    }

    private void readAllTimeout(InputStream is, byte[] buffer, int offset, int length, int timeout) throws IOException {
        int initialSocketTimeout = socket.getSoTimeout();
        long deadline = SystemClock.elapsedRealtime() + timeout;
        int len, i = 0;
        try {
            while (i < length && SystemClock.elapsedRealtime() < deadline) {
                int remaining = (int) (deadline - SystemClock.elapsedRealtime());
                if (remaining > 0) socket.setSoTimeout(remaining);

                len = is.read(buffer, i + offset, length - i);
                if (len < 0) throw new IOException("EOS (" + len + ")");
                i += len;
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("read timed out", e);
        }

        if (i < length) throw new IOException("read timed out");
        socket.setSoTimeout(initialSocketTimeout);
    }

    private void writeMsg(IPCMessage msg) throws IOException {
        synchronized (writeLock) {
            os.write(msg.getBuffer(), 0, IPCMessage.HEADER_LENGTH + msg.getDataLength());
        }
    }

    private void readMsg(IPCMessage msg) throws IOException {
        readAll(is, msg.getBuffer(), 0, IPCMessage.HEADER_LENGTH);
        if (msg.getDataLength() > 0)
            readAll(is, msg.getBuffer(), IPCMessage.HEADER_LENGTH, msg.getDataLength());
    }

    private void sendReply(Reply reply, int messageId) throws IOException {
        writeMsg(new IPCMessage(writeBuffer)
                .clear()
                .setMessageId(messageId)
                .setFlags(IPCConstants.FLAG_REPLY)
                .setCommand(reply.status)
                .copyData(reply.data, reply.offset, reply.length));
    }

    private void writeLoop() {
        try {
            while (socket.isConnected() && !dead) {
                IPCMessage msg = outgoingMessageQueue.poll();

                if (msg == null) {
                    synchronized (outgoingMessageQueue) {
                        if (!outgoingMessageQueue.isEmpty()) continue;
                        outgoingMessageQueue.wait();
                        continue;
                    }
                }

                writeMsg(msg);
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException in write loop", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "write loop interrupted");
        }
    }

    private void connectionLoop() {
        // read loop, write is handled by calling thread
        Log.d(TAG, "start connection loop");
        try {
            // auth
            socket.setSoTimeout(AUTH_TIMEOUT);
            os.write(tokenA, 0, tokenA.length);
            readAllTimeout(is, readBuffer, 0, tokenB.length, AUTH_TIMEOUT);

            boolean valid = true;
            for (int i = 0; i < tokenB.length; i++) {
                if (tokenB[i] == readBuffer[i]) continue;
                valid = false;
                // don't break: timing could give away token
            }

            if (!valid) {
                Log.e(TAG, "auth failed for " + socket.getRemoteSocketAddress());
                return;
            }

            Log.i(TAG, "auth succeeded for " + socket.getRemoteSocketAddress());
            socket.setSoTimeout(0);
            initLatch.countDown();
            onInitComplete(true);

            IPCMessage msg = new IPCMessage(readBuffer);
            while (socket.isConnected() && !dead) {
                readMsg(msg);

                if (msg.isReply()) {
                    SecAdapter<Reply> adapter = pendingReplyMap.remove(msg.getMessageId());
                    if (adapter == null) {
                        Log.wtf(TAG, "couldn't find pending reply entry for reply message: " + msg);
                        continue;
                    }

                    adapter.provideResult(Reply.copyFromMessage(msg));
                    continue;
                }

                Reply reply = null;
                try {
                    reply = onCommand(msg.getCommand(), msg.getBuffer(), IPCMessage.HEADER_LENGTH, msg.getDataLength());
                    assert reply != null; // should never be null
                } catch (Throwable t) {
                    Log.e(TAG, "exception in onCommand()", t);
                }

                if (reply != null) {
                    sendReply(reply, msg.getMessageId());
                } else {
                    sendReply(new Reply(IPCConstants.REPLY_FAILURE), msg.getMessageId());
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "IOException in connection loop", e);
        } finally {
            if (initLatch.getCount() > 0) {
                initLatch.countDown();
                onInitComplete(false);
            }
            close();
        }
    }
}
