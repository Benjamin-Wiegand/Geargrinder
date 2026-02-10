package io.benwiegand.projection.geargrinder.message;

import static io.benwiegand.projection.geargrinder.util.ByteUtil.readUInt16;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.readInt32;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeInt32;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeUInt16;

import java.nio.ByteBuffer;

public class AAFrame {
    public static final int CHANNEL_ID_OFFSET = 0;
    public static final int FLAGS_OFFSET = 1;
    public static final int PAYLOAD_LENGTH_OFFSET = 2;
    public static final int TOTAL_MESSAGE_LENGTH_OFFSET = 4;    // extended header only

    public static final int HEADER_LENGTH = 4;
    public static final int EXTENDED_HEADER_LENGTH = 8;

    public static final int COMMAND_ID_LENGTH = 2;

    public static final int MAX_LENGTH = 0x4000;    // 16 KiB
    public static final int PAYLOAD_MAX_LENGTH = MAX_LENGTH - HEADER_LENGTH;
    public static final int EXTENDED_PAYLOAD_MAX_LENGTH = MAX_LENGTH - EXTENDED_HEADER_LENGTH;

    public static final int FLAG_SEQUENCE_FIRST = 1;
    public static final int FLAG_SEQUENCE_LAST = 1 << 1;
    public static final int FLAG_CONTROL = 1 << 2;
    public static final int FLAG_ENCRYPTED = 1 << 3;

    public static final int SEQUENCE_FLAGS_MASK = FLAG_SEQUENCE_FIRST | FLAG_SEQUENCE_LAST;

    private final byte[] buffer;

    public AAFrame(byte[] buffer, int flags) {
        assert buffer.length >= HEADER_LENGTH;
        this.buffer = buffer;
        setFlags(flags);
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public boolean isFirstInSequence() {
        return (getFlags() & SEQUENCE_FLAGS_MASK) == FLAG_SEQUENCE_FIRST;
    }

    public int getLength() {
        return getPayloadOffset() + getPayloadLength();
    }

    public int getPayloadOffset() {
        return isFirstInSequence() ? EXTENDED_HEADER_LENGTH : HEADER_LENGTH;
    }

    public int getChannelId() {
        return buffer[CHANNEL_ID_OFFSET];
    }

    public AAFrame setChannelId(int channelId) {
        assert channelId <= 0xff && channelId >= 0;
        buffer[CHANNEL_ID_OFFSET] = (byte) channelId;
        return this;
    }

    public int getFlags() {
        return buffer[FLAGS_OFFSET];
    }

    public AAFrame setFlags(int flags) {
        assert flags <= 0xff && flags >= 0;
        buffer[FLAGS_OFFSET] = (byte) flags;
        return this;
    }

    public int getPayloadLength() {
        return readUInt16(buffer, PAYLOAD_LENGTH_OFFSET);
    }

    public AAFrame setPayloadLength(int payloadLength) {
        assert payloadLength <= 0xffff && payloadLength >= 0;
        assert buffer.length - getPayloadOffset() >= payloadLength;
        writeUInt16(payloadLength, buffer, PAYLOAD_LENGTH_OFFSET);
        return this;
    }

    public int getTotalMessageLength() {
        if (!isFirstInSequence()) return getPayloadLength();
        return readInt32(getBuffer(), TOTAL_MESSAGE_LENGTH_OFFSET);
    }

    public AAFrame setTotalMessageLength(int totalLength) {
        assert isFirstInSequence();
        writeInt32(totalLength, buffer, TOTAL_MESSAGE_LENGTH_OFFSET);
        return this;
    }

    public AAFrame copyPayload(byte[] src, int offset, int length) {
        setPayloadLength(length);
        System.arraycopy(src, offset, buffer, getPayloadOffset(), length);
        return this;
    }

    public AAFrame copyPayload(ByteBuffer src) {
        setPayloadLength(src.remaining());
        src.get(buffer, getPayloadOffset(), src.remaining());
        return this;
    }

}
