package io.benwiegand.projection.libprivd.ipc;

import static io.benwiegand.projection.libprivd.util.ByteUtil.readUInt16;
import static io.benwiegand.projection.libprivd.util.ByteUtil.writeUInt16;

import java.util.Arrays;

public final class IPCMessage {
    private static final int MESSAGE_ID_OFFSET = 0;
    private static final int FLAGS_OFFSET = 2;
    private static final int CMD_OFFSET = 3;
    private static final int DATA_LEN_OFFSET = 4;
    private static final int DATA_OFFSET = 6;

    public static final int HEADER_LENGTH = 6;
    private final byte[] buffer;

    public IPCMessage(byte[] buffer) {
        this.buffer = buffer;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int getMessageId() {
        return readUInt16(buffer, MESSAGE_ID_OFFSET);
    }

    public IPCMessage setMessageId(int msgId) {
        assert msgId >= 0 && msgId <= 0xffff;
        writeUInt16(msgId, buffer, MESSAGE_ID_OFFSET);
        return this;
    }

    public int getFlags() {
        return buffer[FLAGS_OFFSET];
    }

    public IPCMessage setFlags(int flags) {
        assert flags >= 0 && flags <= 0xff;
        buffer[FLAGS_OFFSET] = (byte) flags;
        return this;
    }

    public int getCommand() {
        return buffer[CMD_OFFSET];
    }

    public IPCMessage setCommand(int cmd) {
        assert cmd >= 0 && cmd <= 0xff;
        buffer[CMD_OFFSET] = (byte) cmd;
        return this;
    }

    public int getDataLength() {
        return readUInt16(buffer, DATA_LEN_OFFSET);
    }

    public IPCMessage setDataLength(int dataLength) {
        assert dataLength >= 0 && dataLength <= 0xffff;
        writeUInt16(dataLength, buffer, DATA_LEN_OFFSET);
        return this;
    }

    public boolean isReply() {
        return (getFlags() & IPCConstants.FLAG_REPLY) != 0;
    }

    public IPCMessage copyData(byte[] src, int offset, int length) {
        System.arraycopy(src, offset, buffer, DATA_OFFSET, length);
        setDataLength(length);
        return this;
    }

    public IPCMessage clear() {
        Arrays.fill(buffer, 0, HEADER_LENGTH, (byte) 0);
        return this;
    }

    @Override
    public String toString() {
        return "IPCMessage{" +
                "buffer=(" + buffer.length + " bytes)" +
                ", messageId=" + getMessageId() +
                ", flags=" + getFlags() +
                ", command=" + getCommand() +
                ", dataLength=" + getDataLength() +
                '}';
    }

}
