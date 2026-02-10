package io.benwiegand.projection.geargrinder.proto.data.readable;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record ServiceDiscoveryResponse(
        ChannelMeta[] channelMetadata,      // 1 (repeated)
        String headunitName,                // 2
        String carModel,                    // 3
        String carYear,                     // 4
        String carSerial,                   // 5
        boolean leftHandDrive,              // 6
        String headunitManufacturer,        // 7
        String headunitSoftwareBuild,       // 8
        String headunitSoftwareVersion      // 9
) {
    private static final String TAG = ServiceDiscoveryResponse.class.getSimpleName();

    private static final Set<String> GENERIC_CAR_NAMES = Set.of("Universal", "Generic", "Unknown");

    public String friendlyName() {
        String carName = carModel();
        if (carName == null || GENERIC_CAR_NAMES.contains(carName)) {
            if (headunitName() != null) carName = headunitName();
        }
        if (carName == null) carName = carSerial();
        if (carName == null) carName = "Unknown";

        if (carYear() != null) carName = carYear() + " " + carName;

        return carName;
    }


    public static ServiceDiscoveryResponse parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            // for debugging
            ProtoParser.debugDumpRecursive(buffer, offset, length);

            List<ProtoParser.ProtoField> channelMetaFields = fields.get(1);
            ChannelMeta[] channelMetas = new ChannelMeta[channelMetaFields == null ? 0 : channelMetaFields.size()];
            if (channelMetaFields != null) {
                int i = 0;
                for (ProtoParser.ProtoField channelMetaField : channelMetaFields) {
                    if (channelMetaField instanceof ProtoParser.ProtoVarData vd) {
                        channelMetas[i++] = ChannelMeta.parse(buffer, vd.offset(), vd.length());
                    } else {
                        throw new AssertionError("expected var data for channel metadata");
                    }
                }
            }


            return new ServiceDiscoveryResponse(
                    channelMetas,
                    ProtoParser.getSingleString(buffer, fields.get(2), null),
                    ProtoParser.getSingleString(buffer, fields.get(3), null),
                    ProtoParser.getSingleString(buffer, fields.get(4), null),
                    ProtoParser.getSingleString(buffer, fields.get(5), null),
                    ProtoParser.getSingleBoolean(buffer, fields.get(6), true),  // leftHandDrive
                    ProtoParser.getSingleString(buffer, fields.get(7), null),
                    ProtoParser.getSingleString(buffer, fields.get(8), null),
                    ProtoParser.getSingleString(buffer, fields.get(9), null)
            );
        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse ServiceDiscoveryReponse: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

}
