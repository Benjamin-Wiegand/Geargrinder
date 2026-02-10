package io.benwiegand.projection.geargrinder.message;

import static io.benwiegand.projection.geargrinder.message.AAFrame.COMMAND_ID_LENGTH;
import static io.benwiegand.projection.geargrinder.message.AAFrame.EXTENDED_PAYLOAD_MAX_LENGTH;
import static io.benwiegand.projection.geargrinder.message.AAFrame.FLAG_CONTROL;
import static io.benwiegand.projection.geargrinder.message.AAFrame.FLAG_ENCRYPTED;
import static io.benwiegand.projection.geargrinder.message.AAFrame.FLAG_SEQUENCE_FIRST;
import static io.benwiegand.projection.geargrinder.message.AAFrame.FLAG_SEQUENCE_LAST;
import static io.benwiegand.projection.geargrinder.message.AAFrame.HEADER_LENGTH;
import static io.benwiegand.projection.geargrinder.message.AAFrame.PAYLOAD_MAX_LENGTH;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.readUInt16;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeUInt16;

import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.benwiegand.projection.geargrinder.callback.MessageListener;
import io.benwiegand.projection.geargrinder.crypto.TLSService;
import io.benwiegand.projection.geargrinder.transfer.AATransferInterface;
import io.benwiegand.projection.geargrinder.util.ByteUtil;

public class MessageBroker {
    private static final String TAG = MessageBroker.class.getSimpleName();

    private static final int THREAD_POOL_SIZE = 3;
    private static final long THREAD_POOL_KEEP_ALIVE = 100; // ms

    /**
     * how many buffers to allocate for writing sequences.
     * each buffer uses {@link AAFrame#MAX_LENGTH} bytes.
     */
//    private static final int WRITE_BUFFERS = 4;
    private static final int WRITE_BUFFERS = 8; // TODO

    // logs payloads for debugging
    private static final boolean LOG_MESSAGE_DEBUG = false;

    private final Object writeLock = new Object();

    private final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(THREAD_POOL_SIZE, THREAD_POOL_SIZE, THREAD_POOL_KEEP_ALIVE, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>());
    private final Map<Integer, MessageListener> messageHandlers = new HashMap<>();

    private final byte[] readBuffer = new byte[AAFrame.MAX_LENGTH];
    private final byte[][] writeBuffers = new byte[WRITE_BUFFERS][AAFrame.MAX_LENGTH];

    private final AATransferInterface transferInterface;
    private final TLSService tlsService;

    public record MessageSendParameters(
            int channelId,
            boolean encrypted,
            boolean control
    ) {
        private int getFlags() {
            return (encrypted() ? FLAG_ENCRYPTED : 0)
                    | (control() ? FLAG_CONTROL : 0);
        }
    }


    public MessageBroker(AATransferInterface transferInterface, TLSService tlsService) {
        this.transferInterface = transferInterface;
        this.tlsService = tlsService;
    }

    public void destroy() {
        Log.i(TAG, "destroying message broker");
        closeConnection();
        messageHandlers.clear();
        threadPool.shutdown();
    }


    public void closeConnection() {
        if (!transferInterface.alive()) return;

        try {
            transferInterface.close();
        } catch (IOException e) {
            Log.w(TAG, "IOException while closing connection", e);
        }
    }

    public boolean isAlive() {
        return transferInterface.alive();
    }

    public int getMaxPayloadSize(boolean encrypted) {
        if (encrypted)
            return tlsService.getMaxPlaintextSize(EXTENDED_PAYLOAD_MAX_LENGTH) + tlsService.getMaxPlaintextSize(PAYLOAD_MAX_LENGTH) * (writeBuffers.length - 1);
        return EXTENDED_PAYLOAD_MAX_LENGTH + PAYLOAD_MAX_LENGTH * (writeBuffers.length - 1);
    }

    private int calculateSequenceLength(int extendedPayloadMaxLength, int payloadMaxLength, int length) {
        if (length <= payloadMaxLength) return 1;
        int fullFrames = (length - extendedPayloadMaxLength) / payloadMaxLength + 1;
        return (length - extendedPayloadMaxLength) % payloadMaxLength != 0 ? fullFrames + 1 : fullFrames;
    }

    /**
     * sends an AAFrame.
     * @param frame the frame to send
     */
    private void sendFrame(AAFrame frame) {
        synchronized (writeLock) {
            try {
                transferInterface.sendFrame(frame.getBuffer(), 0, frame.getLength());
            } catch (IOException e) {
                Log.e(TAG, "IOException while sending message", e);
            }
        }
    }

    /**
     * sends a full message with a payload
     * @param params the parameters to send the message with
     * @param payload a byte array containing the payload
     * @param offset the offset of the payload
     * @param length the length of the payload
     */
    public void sendMessage(MessageSendParameters params, byte[] payload, int offset, int length) {
        synchronized (writeLock) {
            try {
                int extendedPayloadMaxLength = params.encrypted() ? tlsService.getMaxPlaintextSize(EXTENDED_PAYLOAD_MAX_LENGTH) : EXTENDED_PAYLOAD_MAX_LENGTH;
                int payloadMaxLength = params.encrypted() ? tlsService.getMaxPlaintextSize(PAYLOAD_MAX_LENGTH) : PAYLOAD_MAX_LENGTH;
                int sequenceLength = calculateSequenceLength(extendedPayloadMaxLength, payloadMaxLength, length);

                if (sequenceLength > writeBuffers.length)
                    throw new IndexOutOfBoundsException("not enough write buffers to send message of size " + length);

                // single frame
                if (sequenceLength == 1) {
                    int flags = params.getFlags() | FLAG_SEQUENCE_FIRST | FLAG_SEQUENCE_LAST;
                    AAFrame frame = new AAFrame(writeBuffers[0], flags)
                            .setChannelId(params.channelId());

                    if (params.encrypted()) {
                        tlsService.encrypt(payload, offset, length, frame::copyPayload);
                    } else {
                        frame.copyPayload(payload, offset, length);
                    }

                    sendFrame(frame);
                    return;
                }

                // split into sequence
                AAFrame[] frames = new AAFrame[sequenceLength];
                boolean firstInSequence, lastInSequence;
                int flags;
                int curOffset = offset;
                int curLength = Math.min(length, extendedPayloadMaxLength);
                for (int i = 0; i < sequenceLength; i++) {
                    firstInSequence = curOffset == offset;
                    lastInSequence = offset + length == curOffset + curLength;
                    flags = params.getFlags()
                            | (firstInSequence ? FLAG_SEQUENCE_FIRST : 0)
                            | (lastInSequence ? FLAG_SEQUENCE_LAST : 0);

                    frames[i] = new AAFrame(writeBuffers[i], flags)
                            .setChannelId(params.channelId());

                    if (params.encrypted()) {
                        tlsService.encrypt(payload, curOffset, curLength, frames[i]::copyPayload);
                    } else {
                        frames[i].copyPayload(payload, curOffset, curLength);
                    }

                    curOffset += curLength;
                    curLength = Math.min(length - (curOffset - offset), payloadMaxLength);
                }

                // first in sequence must have total length
                // but the total length is unknown if encrypted
                int totalLength = 0;
                for (AAFrame frame : frames)
                    totalLength += frame.getPayloadLength();
                frames[0].setTotalMessageLength(totalLength);

                for (AAFrame frame : frames)
                    sendFrame(frame);

            } catch (IOException e) {
                Log.e(TAG, "IOException while sending message", e);
            }
        }
    }

    /**
     * like {@link MessageBroker#sendMessage(MessageSendParameters, byte[], int, int)} but uses the whole payload byte array
     */
    public void sendMessage(MessageSendParameters params, byte[] payload) {
        sendMessage(params, payload, 0, payload.length);
    }

    /**
     * sends a full message with a payload constructed from the provided command and command data.
     * avoid this version of sendMessage() for frequently sent encrypted messages if possible to avoid the arraycopy used to construct the payload.
     * @param params the parameters to send message with
     * @param cmd the command (unsigned, 16 bits max)
     * @param cmdData the data following the command
     */
    public void sendMessage(MessageSendParameters params, int cmd, byte[] cmdData) {
        assert cmd >= 0 && cmd <= 0xffff;
        synchronized (writeLock) {
            byte[] payload = new byte[COMMAND_ID_LENGTH + cmdData.length];
            writeUInt16(cmd, payload, 0);
            System.arraycopy(cmdData, 0, payload, COMMAND_ID_LENGTH, cmdData.length);
            sendMessage(params, payload);
        }
    }

    /**
     * like {@link MessageBroker#sendMessage(MessageSendParameters, int cmd, byte[] cmdData)} but with no command data.
     */
    public void sendMessage(MessageSendParameters params, int cmd) {
        assert cmd >= 0 && cmd <= 0xffff;
        synchronized (writeLock) {
            byte[] payload = new byte[COMMAND_ID_LENGTH];
            writeUInt16(cmd, payload, 0);
            sendMessage(params, payload);
        }
    }

    public void registerForChannel(int channelId, MessageListener handler) {
        if (messageHandlers.containsKey(channelId)) throw new IllegalArgumentException("handler already registered for channel " + channelId);
        messageHandlers.put(channelId, handler);
    }

    private void handleMessage(int length) throws IOException {

        // TODO: handle message sequences. not a big deal as they are unlikely to occur since usually only the phone sends large amounts of data

        if (length < HEADER_LENGTH) throw new RuntimeException("message too short");

        // header
        int channelId = readBuffer[0];
        int flags = readBuffer[1];
        int payloadLength = readUInt16(readBuffer, 2);

        if (payloadLength + HEADER_LENGTH < length) {
            Log.w(TAG, "message larger than payload");
        } else if (payloadLength + HEADER_LENGTH > length) {
            throw new AssertionError("message too small for payload size");
        }

        // decrypt payload
        byte[] payload;
        if ((flags & FLAG_ENCRYPTED) != 0) {
            if (tlsService.needsHandshake()) {
                Log.wtf(TAG, "encrypted message before handshake?!");
                if (LOG_MESSAGE_DEBUG) Log.d(TAG, "RX raw: " + ByteUtil.hexDump(readBuffer, 0, length));
                throw new AssertionError("received encrypted message before ssl/tls handshake");
            }

            payload = tlsService.decrypt(readBuffer, HEADER_LENGTH, payloadLength, out -> {
                byte[] decryptedPayload = new byte[out.remaining()];
                out.get(decryptedPayload);
                return decryptedPayload;
            });
        } else {
            // not encrypted
            payload = new byte[payloadLength];
            System.arraycopy(readBuffer, HEADER_LENGTH, payload, 0, payloadLength);
        }

        if (LOG_MESSAGE_DEBUG) Log.d(TAG, "RX payload: " + ByteUtil.hexDump(payload));

        // callback
        MessageListener handler = messageHandlers.get(channelId);
        if (handler == null) {
            Log.w(TAG, "no handler for channel " + channelId);
            return;
        }

        threadPool.execute(() -> {
            try {
                handler.onMessage(channelId, flags, payload, 0, payload.length);
            } catch (Throwable t) {
                Log.wtf(TAG, "exception in message handler", t);
                closeConnection();  // this isn't supposed to happen
            }
        });
    }

    public void loop() {
        Log.i(TAG, "connection loop start");
        try {
            int len;
            while (transferInterface.alive()) {
                len = transferInterface.readFrame(readBuffer);
                if (len == 0) continue;

                handleMessage(len);
            }
        } catch (IOException e) {
            Log.v(TAG, "connection died", e);
        } finally {
            Log.d(TAG, "connection loop exit");
        }
    }

}
