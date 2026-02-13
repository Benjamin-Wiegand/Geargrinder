package io.benwiegand.projection.libprivd.util;

public class ByteUtil {

    public static int unsignByte(byte b) {
        return Byte.toUnsignedInt(b);
    }

    public static int writeUInt16(int value, byte[] buffer, int offset) {
        assert value >= 0;
        assert value <= 0xFFFF;
        buffer[offset] = (byte) (value >> 8);
        buffer[offset+1] = (byte) (value & 0xff);
        return 2;
    }

    public static int readUInt16(byte[] buffer, int offset) {
        return (unsignByte(buffer[offset]) << 8) + unsignByte(buffer[offset + 1]);
    }
}
