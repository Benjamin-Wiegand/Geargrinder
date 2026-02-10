package io.benwiegand.projection.geargrinder.proto;

import static io.benwiegand.projection.geargrinder.util.ByteUtil.unsignByte;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeInt32;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeInt64;

/**
 * flexible protobuf serializer
 */
public class ProtoSerializer {

    private static final int FIELD_TYPE_VAR_INT = 0;
    private static final int FIELD_TYPE_64 = 1;
    private static final int FIELD_TYPE_VAR_DATA = 2;
    private static final int FIELD_TYPE_GROUP_START = 3;
    private static final int FIELD_TYPE_GROUP_END = 4;
    private static final int FIELD_TYPE_32 = 5;

    public interface ProtoField {
        int fieldId();

        int encode(byte[] buffer, int offset);

        int encodedLength();

        int type();
    }

    public record ProtoVarInt(int fieldId, long value) implements ProtoSerializer.ProtoField {
        @Override
        public int encode(byte[] buffer, int offset) {
            int i = offset;
            long v = value();
            do {
                buffer[i++] = (byte) ((v & 0x7f) | 0x80);
                v >>= 7;
            } while (v != 0);
            buffer[i-1] = (byte) (unsignByte(buffer[i-1]) & 0x7f);

            return i - offset;
        }

        @Override
        public int encodedLength() {
            int i = 1;
            long v = value();
            while ((v >>= 7) != 0) i++;
            return i;
        }

        @Override
        public int type() {
            return FIELD_TYPE_VAR_INT;
        }
    }
    public record ProtoVarData(int fieldId, byte[] data) implements ProtoSerializer.ProtoField {
        @Override
        public int encode(byte[] buffer, int offset) {
            int i = offset;
            i += new ProtoVarInt(0, data.length).encode(buffer, i);

            System.arraycopy(data, 0, buffer, i, data.length);
            i += data.length;
            return i - offset;
        }

        @Override
        public int encodedLength() {
            return data.length + new ProtoVarInt(0, data.length).encodedLength();
        }

        @Override
        public int type() {
            return FIELD_TYPE_VAR_DATA;
        }
    }
    public record Proto64(int fieldId, long value) implements ProtoSerializer.ProtoField {
        @Override
        public int encode(byte[] buffer, int offset) {
            writeInt64(value(), buffer, offset);
            return encodedLength();
        }

        @Override
        public int encodedLength() {
            return 8;
        }

        @Override
        public int type() {
            return FIELD_TYPE_64;
        }
    }
    public record Proto32(int fieldId, int value) implements ProtoSerializer.ProtoField {
        @Override
        public int encode(byte[] buffer, int offset) {
            writeInt32(value(), buffer, offset);
            return encodedLength();
        }

        @Override
        public int encodedLength() {
            return 4;
        }

        @Override
        public int type() {
            return FIELD_TYPE_32;
        }
    }

    public static byte[] serialize(ProtoField... fields) {
        int totalLength = 0;
        for (ProtoField field : fields) {
            assert field.fieldId() > 0;
            assert field.fieldId() < 0xf8;

            totalLength += 1 + field.encodedLength();
        }

        byte[] buffer = new byte[totalLength];
        int i = 0;
        for (ProtoField field : fields) {
            int fieldType = field.type();
            buffer[i++] = (byte) ((field.fieldId() << 3) + fieldType);
            i += field.encode(buffer, i);
        }
        return buffer;
    }

}
