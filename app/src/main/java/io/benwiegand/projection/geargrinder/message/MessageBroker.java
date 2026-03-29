package io.benwiegand.projection.geargrinder.message;

import static io.benwiegand.projection.geargrinder.message.AAFrame.COMMAND_ID_LENGTH;
import static io.benwiegand.projection.geargrinder.message.AAFrame.EXTENDED_HEADER_LENGTH;
import static io.benwiegand.projection.geargrinder.message.AAFrame.EXTENDED_PAYLOAD_MAX_LENGTH;
import static io.benwiegand.projection.geargrinder.message.AAFrame.FLAG_CONTROL;
import static io.benwiegand.projection.geargrinder.message.AAFrame.FLAG_ENCRYPTED;
import static io.benwiegand.projection.geargrinder.message.AAFrame.FLAG_SEQUENCE_FIRST;
import static io.benwiegand.projection.geargrinder.message.AAFrame.FLAG_SEQUENCE_LAST;
import static io.benwiegand.projection.geargrinder.message.AAFrame.HEADER_LENGTH;
import static io.benwiegand.projection.geargrinder.message.AAFrame.PAYLOAD_MAX_LENGTH;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeUInt16;

import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.benwiegand.projection.geargrinder.callback.MessageListener;
import io.benwiegand.projection.geargrinder.crypto.TLSService;
import io.benwiegand.projection.geargrinder.transfer.AATransferInterface;
import io.benwiegand.projection.geargrinder.util.ByteUtil;

public class MessageBroker {
    private static final String TAG = MessageBroker.class.getSimpleName();

    private static final int INIT_PAYLOAD_BUFFER_SIZE = PAYLOAD_MAX_LENGTH;

    // logs payloads for debugging
    private static final boolean LOG_MESSAGE_DEBUG = false;

    private final Object writeLock = new Object();

    private final Map<Integer, MessageListener> messageHandlers = new HashMap<>();

    // max outbound length is 16 KiB but frames are technically capable of an additional 8 bytes beyond that
    private final byte[] readBuffer = new byte[AAFrame.MAX_LENGTH + EXTENDED_HEADER_LENGTH];
    private byte[] readMessageBuffer = new byte[INIT_PAYLOAD_BUFFER_SIZE];

    private final byte[] writeBuffer = new byte[AAFrame.MAX_LENGTH];

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

    private int calculateSequenceLength(int extendedPayloadMaxLength, int payloadMaxLength, int length) {
        if (length <= payloadMaxLength) return 1;
        int fullFrames = (length - extendedPayloadMaxLength) / payloadMaxLength + 1;
        return (length - extendedPayloadMaxLength) % payloadMaxLength != 0 ? fullFrames + 1 : fullFrames;
    }

    private void growReadMessageBufferIfNeeded(int newSize, int copy) {
        assert copy <= readMessageBuffer.length;
        if (readMessageBuffer.length >= newSize) return;

        Log.d(TAG, "resizing message buffer to " + newSize + " bytes (retaining " + copy + " bytes from start of buffer)");

        byte[] buffer = new byte[newSize];
        if (copy > 0)
            System.arraycopy(readMessageBuffer, 0, buffer, 0, copy);

        readMessageBuffer = buffer;
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

                // single frame
                if (sequenceLength == 1) {
                    int flags = params.getFlags() | FLAG_SEQUENCE_FIRST | FLAG_SEQUENCE_LAST;
                    AAFrame frame = new AAFrame(writeBuffer, flags)
                            .setChannelId(params.channelId());

                    if (params.encrypted()) {
                        tlsService.encrypt(payload, offset, length, frame::copyPayload);
                    } else {
                        frame.copyPayload(payload, offset, length);
                    }

                    sendFrame(frame);
                    return;
                }

                // multi-frame
                Log.v(TAG, "multi-frame message of len " + sequenceLength);
                int payloadOffset = offset;
                int payloadRemaining = length;
                int payloadLength;
                for (int i = 0; i < sequenceLength; i++) {
                    int flags = params.getFlags();
                    if (i == 0) flags |= FLAG_SEQUENCE_FIRST;
                    if (i == sequenceLength - 1) flags |= FLAG_SEQUENCE_LAST;

                    AAFrame frame = new AAFrame(writeBuffer, flags)
                            .setChannelId(params.channelId());

                    if (i == 0) {
                        frame.setTotalMessageLength(length);
                        payloadLength = Math.min(extendedPayloadMaxLength, payloadRemaining);
                    } else {
                        payloadLength = Math.min(payloadMaxLength, payloadRemaining);
                    }

                    tlsService.encrypt(payload, payloadOffset, payloadLength, frame::copyPayload);
                    sendFrame(frame);

                    payloadOffset += payloadLength;
                    payloadRemaining -= payloadLength;
                }

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

    private AAFrame readFrame() throws IOException {
        int len = 0;
        while (len == 0) {
            len = transferInterface.readFrame(readBuffer);
            if (LOG_MESSAGE_DEBUG) Log.d(TAG, "received frame: len = " + len);
            if (LOG_MESSAGE_DEBUG) Log.d(TAG, "RX raw: " + ByteUtil.hexDump(readBuffer, 0, len));
            if (len == 0) Log.w(TAG, "got empty frame");
        }

        if (len < HEADER_LENGTH) throw new RuntimeException("transfer too short");

        AAFrame frame = new AAFrame(readBuffer);
        if (LOG_MESSAGE_DEBUG) Log.d(TAG, "received frame: " + frame);

        if (frame.getLength() < len) {
            Log.w(TAG, "frame smaller than transfer");
        } else if (frame.getLength() > len) {
            throw new AssertionError("transfer too small for frame");
        }

        return frame;
    }

    private void readMessage() throws IOException {
        AAFrame frame = readFrame();
        int messageLength = 0;
        int messageIndex = 0;

        // can't decrypt with no keys
        if (frame.isPayloadEncrypted() && tlsService.needsHandshake()) {
            Log.wtf(TAG, "encrypted message before handshake?!");
            throw new AssertionError("received encrypted message before ssl/tls handshake");
        }

        // should always start with first frame
        if (frame.isInSequence() && !frame.isFirstInSequence()) {
            Log.wtf(TAG, "multi-frame message didn't start with first frame");
            throw new AssertionError("multi-frame message didn't start with first frame");
        }

        // multi-frame length
        if (frame.isFirstInSequence()) {
            messageLength = frame.getTotalMessageLength();
            growReadMessageBufferIfNeeded(frame.getTotalMessageLength(), 0);
        } else if (!frame.isPayloadEncrypted()) {
            messageLength = frame.getTotalMessageLength();
            growReadMessageBufferIfNeeded(messageLength, 0);
        }

        // decrypt first frame
        if (frame.isPayloadEncrypted()) {
            int firstDecryptedPayloadLength = tlsService.decrypt(frame.getBuffer(), frame.getPayloadOffset(), frame.getPayloadLength(), out -> {
                int decryptedLength = out.remaining();
                growReadMessageBufferIfNeeded(decryptedLength, 0);
                out.get(readMessageBuffer, 0, decryptedLength);
                return decryptedLength;
            });

            messageIndex += firstDecryptedPayloadLength;
            if (!frame.isInSequence())
                messageLength = firstDecryptedPayloadLength;

        } else {
            System.arraycopy(frame.getBuffer(), frame.getPayloadOffset(), readMessageBuffer, 0, frame.getPayloadLength());
            messageIndex = frame.getPayloadLength();
        }

        // multi-frame messages
        if (frame.isFirstInSequence()) {
            boolean encrypted = frame.isPayloadEncrypted();
            int channelId = frame.getChannelId();
            frame = readFrame();
            do {
                readFrame();
                if (LOG_MESSAGE_DEBUG) Log.d(TAG, "received next frame in sequence: " + frame);
                if (!frame.isInSequence() || frame.isFirstInSequence() || frame.getChannelId() != channelId || frame.isPayloadEncrypted() != encrypted)
                    throw new AssertionError("broken frame sequence: channelId=" + channelId + ", encrypted=" + encrypted + ", frame=" + frame);

                if (frame.isPayloadEncrypted()) {
                    int curMessageIndex = messageIndex;
                    messageIndex += tlsService.decrypt(frame.getBuffer(), frame.getPayloadOffset(), frame.getPayloadLength(), out -> {
                        int decryptedLength = out.remaining();
                        out.get(readMessageBuffer, curMessageIndex, decryptedLength);
                        return decryptedLength;
                    });
                } else {
                    System.arraycopy(frame.getBuffer(), frame.getPayloadOffset(), readMessageBuffer, messageIndex, frame.getPayloadLength());
                    messageIndex += frame.getPayloadLength();
                }

            } while (!frame.isLastInSequence());
        }

        if (LOG_MESSAGE_DEBUG) Log.d(TAG, "RX message: " + ByteUtil.hexDump(readMessageBuffer, 0, messageLength));

        if (messageIndex != messageLength)
            throw new AssertionError("message length miss-match: read=" + messageIndex + " expect=" + messageLength);

        // callback
        MessageListener handler = messageHandlers.get(frame.getChannelId());
        if (handler == null) {
            Log.w(TAG, "no handler for channel " + frame.getChannelId());
            return;
        }

        try {
            handler.onMessage(frame.getChannelId(), frame.getFlags(), readMessageBuffer, 0, messageLength);
        } catch (Throwable t) {
            Log.wtf(TAG, "exception in message handler", t);
            closeConnection();  // this isn't supposed to happen
        }
    }

    public void loop() {
        Log.i(TAG, "connection loop start");
        try {

            while (transferInterface.alive())
                readMessage();

        } catch (IOException e) {
            Log.v(TAG, "connection died", e);
        } finally {
            Log.d(TAG, "connection loop exit");
        }
    }

}
