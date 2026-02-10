package io.benwiegand.projection.geargrinder.util;

public class ByteUtil {

    private static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final char[] HEX_DIGITS_UPPER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String hexDump(byte[] buffer, int offset, int length, String separator, boolean upper) {
        char[] digits = upper ? HEX_DIGITS_UPPER : HEX_DIGITS;

        StringBuilder sb = new StringBuilder((length - offset) * (2 + separator.length()));
        for (int i = offset; i < length; i++) {
            byte b = buffer[i];
            sb.append(digits[(0xF0 & b) >>> 4])
                    .append(digits[0x0F & b]);

            if (i != (buffer.length - offset) - 1) sb.append(separator);
        }

        return sb.toString();
    }

    public static String hexDump(byte[] buffer, int offset, int length) {
        return hexDump(buffer, offset, length, " ", false);
    }

    public static String hexDump(byte[] buffer) {
        return hexDump(buffer, 0, buffer.length);
    }

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

    public static int writeInt32(int value, byte[] buffer, int offset) {
        buffer[offset++] = (byte) ((value >> 24) & 0xff);
        buffer[offset++] = (byte) ((value >> 16) & 0xff);
        buffer[offset++] = (byte) ((value >> 8) & 0xff);
        buffer[offset] = (byte) (value & 0xff);
        return 8;
    }

    public static int writeInt64(long value, byte[] buffer, int offset) {
        buffer[offset++] = (byte) ((value >> 56) & 0xff);
        buffer[offset++] = (byte) ((value >> 48) & 0xff);
        buffer[offset++] = (byte) ((value >> 40) & 0xff);
        buffer[offset++] = (byte) ((value >> 32) & 0xff);
        buffer[offset++] = (byte) ((value >> 24) & 0xff);
        buffer[offset++] = (byte) ((value >> 16) & 0xff);
        buffer[offset++] = (byte) ((value >> 8) & 0xff);
        buffer[offset] = (byte) (value & 0xff);
        return 8;
    }

    public static int readUInt16(byte[] buffer, int offset) {
        return (unsignByte(buffer[offset]) << 8) + unsignByte(buffer[offset + 1]);
    }

    public static int readInt32(byte[] buffer, int offset) {
        return (unsignByte(buffer[offset++]) << 24) +
                (unsignByte(buffer[offset++]) << 16) +
                (unsignByte(buffer[offset++]) << 8) +
                unsignByte(buffer[offset]);
    }

    public static long readInt64(byte[] buffer, int offset) {
        return ((long) unsignByte(buffer[offset++]) << 56) +
                ((long) unsignByte(buffer[offset++]) << 48) +
                ((long) unsignByte(buffer[offset++]) << 40) +
                ((long) unsignByte(buffer[offset++]) << 32) +
                ((long) unsignByte(buffer[offset++]) << 24) +
                ((long) unsignByte(buffer[offset++]) << 16) +
                ((long) unsignByte(buffer[offset++]) << 8) +
                unsignByte(buffer[offset]);
    }
}
