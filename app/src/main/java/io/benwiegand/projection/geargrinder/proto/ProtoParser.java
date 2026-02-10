package io.benwiegand.projection.geargrinder.proto;

import static io.benwiegand.projection.geargrinder.util.ByteUtil.readInt32;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.readInt64;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.unsignByte;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * flexible protobuf parser
 */
public class ProtoParser {
    private static final String TAG = ProtoParser.class.getSimpleName();

    // TODO: merge parts of ProtoParser and ProtoSerializer
    private static final int FIELD_TYPE_VAR_INT = 0;
    private static final int FIELD_TYPE_64 = 1;
    private static final int FIELD_TYPE_VAR_DATA = 2;
    private static final int FIELD_TYPE_GROUP_START = 3;
    private static final int FIELD_TYPE_GROUP_END = 4;
    private static final int FIELD_TYPE_32 = 5;

    public interface ProtoField {
        int fieldId();
        int offset();
        int length();
    }

    public record ProtoVarInt(int fieldId, int offset, int length) implements ProtoField {
        public long decode(byte[] buffer) {
            int i = offset();
            long value = 0;
            int lshift = 0;
            do {
                value += (long) (buffer[i] & 0x7f) << lshift;
                lshift += 7;
            } while ((buffer[i++] & 0x80) != 0);

            assert i - offset() == length();
            return value;
        }
    }
    public record ProtoVarData(int fieldId, int offset, int length) implements ProtoField {}
    public record Proto64(int fieldId, int offset) implements ProtoField {
        public int length() {
            return 8;
        }
    }
    public record Proto32(int fieldId, int offset) implements ProtoField {
        public int length() {
            return 4;
        }
    }


    public static ProtoField getSingle(List<ProtoField> fieldList) {
        if (fieldList == null) return null;
        if (fieldList.size() != 1) throw new AssertionError("expected one field in list");
        return fieldList.get(0);
    }

    public static <T extends ProtoField> T getSingle(List<ProtoField> fieldList, Class<T> clazz) {
        ProtoField field = getSingle(fieldList);
        if (field == null) return null;
        if (!clazz.equals(field.getClass()))
            throw new AssertionError("expected field of type " + clazz.getSimpleName() + " but got " + field.getClass().getSimpleName());
        return (T) field;
    }

    public static long getSingleUnsignedInteger(byte[] buffer, List<ProtoField> fieldList, long defaultValue) {
        return switch (getSingle(fieldList)) {
            case null -> defaultValue;
            case ProtoVarInt vi -> vi.decode(buffer);
            case Proto32 p32 -> readInt32(buffer, p32.offset());
            case Proto64 p64 -> readInt64(buffer, p64.offset());
            default -> throw new AssertionError("invalid field type for integer");
        };
    }

    public static int getSingleUnsignedInteger32(byte[] buffer, List<ProtoField> fieldList, int defaultValue) {
        return switch (getSingle(fieldList)) {
            case null -> defaultValue;
            case ProtoVarInt vi -> (int) vi.decode(buffer);
            case Proto32 p32 -> readInt32(buffer, p32.offset());
            default -> throw new AssertionError("invalid field type for 32 bit integer");
        };
    }

    public static boolean getSingleBoolean(byte[] buffer, List<ProtoField> fieldList, boolean defaultValue) {
        return getSingleUnsignedInteger(buffer, fieldList, defaultValue ? 1 : 0) != 0;
    }

    public static String getSingleString(byte[] buffer, List<ProtoField> fieldList, String defaultValue) {
        return switch (getSingle(fieldList)) {
            case null -> defaultValue;
            case ProtoVarData vd -> new String(buffer, vd.offset(), vd.length(), StandardCharsets.UTF_8);
            default -> throw new AssertionError("expected var data field type for string");
        };
    }

    public static String getSingleString(byte[] buffer, List<ProtoField> fieldList) {
        return getSingleString(buffer, fieldList, null);
    }

    private static int[] getUnsignedInteger8Array(byte[] buffer, ProtoVarData vd) {
        if (vd == null) return new int[0];

        int[] result = new int[vd.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = unsignByte(buffer[i + vd.offset()]);
        }
        return result;
    }

    public static int[] getUnsignedInteger32Array(byte[] buffer, List<ProtoField> fieldList) {
        if (fieldList == null) return new int[0];

        // seems to be converted to a byte array sometimes when all values are <= 255
        if (fieldList.size() == 1 && fieldList.get(0) instanceof ProtoVarData vd)
            return getUnsignedInteger8Array(buffer, vd);

        int[] result = new int[fieldList.size()];
        int i = 0;
        for (ProtoParser.ProtoField field : fieldList) {
            result[i++] = switch (field) {
                case ProtoVarInt vi -> (int) vi.decode(buffer);
                case Proto32 p32 -> readInt32(buffer, p32.offset());
                default -> throw new AssertionError("invalid field type for 32 bit integer");
            };
        }
        return result;
    }


    public static Map<Integer, List<ProtoField>> parse(byte[] buffer, int offset, int length) {
        Map<Integer, List<ProtoField>> result = new HashMap<>();
        int i = offset;
        while (i - offset < length) {
            ProtoField field = parseField(buffer, i);

            List<ProtoField> fieldList = result.get(field.fieldId());
            if (fieldList == null) fieldList = new ArrayList<>(1);
            fieldList.add(field);
            result.put(field.fieldId(), fieldList);

            i = field.offset() + field.length();
            if (i - offset == length) break;
            if (i - offset > length) {
                // passed length might be incorrect
                Log.wtf(TAG, "exceeded length while parsing field with id: " + field.fieldId());
                throw new IndexOutOfBoundsException("a field parsed beyond the specified length");
            }
        }

        return result;
    }

    private static ProtoVarInt parseVarInt(int fieldId, byte[] buffer, int offset) {
        // uses left-most bit of each byte to indicate end
        int i = offset;
        while ((buffer[i++] & 0x80) != 0) { /* weee */ }
        return new ProtoVarInt(fieldId, offset, i - offset);
    }

    private static ProtoField parseField(byte[] buffer, int offset) {
        int i = offset;
        int fieldId = buffer[i] >> 3;
        int fieldType = buffer[i] & 0x07;
        i++;

        return switch (fieldType) {
            case FIELD_TYPE_64 -> new Proto64(fieldId, i);
            case FIELD_TYPE_32 -> new Proto32(fieldId, i);
            case FIELD_TYPE_VAR_INT -> parseVarInt(fieldId, buffer, i);
            case FIELD_TYPE_VAR_DATA -> {
                ProtoVarInt dataLength = parseVarInt(0, buffer, i);
                i += dataLength.length();
                long lengthDecoded = dataLength.decode(buffer);
                if (lengthDecoded > Integer.MAX_VALUE || lengthDecoded < 0) {
                    Log.wtf(TAG, "unrealistically massive var data: len = " + lengthDecoded);
                    throw new AssertionError("data size too large for field with id " + fieldId);
                }
                yield new ProtoVarData(fieldId, i, (int) lengthDecoded);
            }
            case FIELD_TYPE_GROUP_START -> {
                Log.w(TAG, "skipping group");
                // groups aren't supported
                while (buffer[i] >> 3 != fieldId && (buffer[i++] & 0x07) != FIELD_TYPE_GROUP_END) { /* weee */ }
                final int iFinal = i;
                yield new ProtoField() {
                    @Override
                    public int fieldId() {
                        return fieldId;
                    }

                    @Override
                    public int offset() {
                        return offset + 1;
                    }

                    @Override
                    public int length() {
                        return iFinal - offset();
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "unparsed group (length " + length() + ")";
                    }
                };
            }
            case FIELD_TYPE_GROUP_END -> {
                Log.wtf(TAG, "group end without group start!!!");
                throw new AssertionError("group end without group start for id " + fieldId);
            }

            default -> {
                // this should be impossible
                Log.wtf(TAG, "unexpected field type: " + fieldType);
                throw new AssertionError("unexpected field type " + fieldType + " for field with id " + fieldId);
            }
        };

    }

    private static void debugDumpField(byte[] buffer, ProtoField field, int indentBy) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indentBy; i++) sb.append("  ");
        String indent = sb.toString();

        if (field instanceof ProtoParser.ProtoVarInt vi) {
            Log.i(TAG, indent + "- field " + field.fieldId() + " num : " + vi.decode(buffer));
        } else if (field instanceof ProtoParser.Proto32 p32) {
            Log.i(TAG, indent + "- field " + field.fieldId() + " i32 : " + readInt32(buffer, p32.offset()));
        } else if (field instanceof ProtoParser.Proto64 p64) {
            Log.i(TAG, indent + "- field " + field.fieldId() + " i64 : " + readInt64(buffer, p64.offset()));
        } else if (field instanceof ProtoParser.ProtoVarData vd) {
//                    Log.i(TAG, "field " + id + " dat : " + new String(buffer, vd.offset(), vd.length()));
            Log.i(TAG, indent + "- field " + field.fieldId() + " dat : " + Base64.encodeToString(buffer, vd.offset(), vd.length(), Base64.NO_WRAP));
        }
    }

    private static void debugDumpRecursive(byte[] buffer, Map<Integer, List<ProtoField>> fields, int indent) {

        for (Map.Entry<Integer, List<ProtoParser.ProtoField>> entry : fields.entrySet()) {
            for (ProtoParser.ProtoField field : entry.getValue()) {
                debugDumpField(buffer, field, indent);

                if (field instanceof ProtoVarData vd) {
                    try {
                        Map<Integer, List<ProtoField>> subFields = parse(buffer, vd.offset(), vd.length());
                        debugDumpRecursive(buffer, subFields, indent + 1);
                    } catch (Throwable ignored) {
                        // probably not protobuf data
                    }
                }
            }
        }
    }

    private static void debugDump(byte[] buffer, int offset, int length) {
        Map<Integer, List<ProtoField>> fields = parse(buffer, offset, length);

        for (Map.Entry<Integer, List<ProtoParser.ProtoField>> entry : fields.entrySet()) {
            for (ProtoParser.ProtoField field : entry.getValue()) {
                debugDumpField(buffer, field, 0);
            }
        }
    }

    public static void debugDumpRecursive(byte[] buffer, int offset, int length) {
        Map<Integer, List<ProtoField>> fields = parse(buffer, offset, length);
        debugDumpRecursive(buffer, fields, 0);
    }

}
