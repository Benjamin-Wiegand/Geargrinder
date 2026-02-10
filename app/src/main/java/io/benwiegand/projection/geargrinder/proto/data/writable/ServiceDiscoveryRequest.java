package io.benwiegand.projection.geargrinder.proto.data.writable;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

import io.benwiegand.projection.geargrinder.proto.ProtoSerializer;

public record ServiceDiscoveryRequest(
        byte[] thumbnail32,     // 1    32x32 png image
        byte[] thumbnail64,     // 2    64x64 png image
        byte[] thumbnail128,    // 3    128x128 png image
        String deviceName,      // 4
        String deviceModel      // 5
) {
    public static final byte[] DEFAULT_THUMBNAIL_32 = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAACAAAAAgAgMAAAAOFJJnAAAAAXNSR0IB2cksfwAAAARnQU1BAACxjwv8YQUAAAAgY0hSTQAAeiYAAICEAAD6AAAAgOgAAHUwAADqYAAAOpgAABdwnLpRPAAAAAxQTFRFAAAAAAAA////nZ2detZtKgAAAAF0Uk5TAEDm2GYAAABKSURBVBjTY2AgBBRgDA0YXwPCW4CFsQArowHKYMLK0EBhMCzAYKxawbRq1QogQ2vVKgiDYRVYEqSIAc5QgDEW4GNw4WIoQBhoAAACdyWFYS2uEwAAAABJRU5ErkJggg==", 0);
    public static final byte[] DEFAULT_THUMBNAIL_64 = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAEAAAABAAgMAAADXB5lNAAAAAXNSR0IB2cksfwAAAARnQU1BAACxjwv8YQUAAAAgY0hSTQAAeiYAAICEAAD6AAAAgOgAAHUwAADqYAAAOpgAABdwnLpRPAAAAAxQTFRFAAAAAAAA////nZ2detZtKgAAAAF0Uk5TAEDm2GYAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAAh0lEQVQ4y8XTSQqAMAwF0NCV5Cg9ZY+SZfintFZNYwRRcQgUyqMhA5To8+ASIF+DKX8D2UOSAHwdkp4B7MB3yjsQD7XNAGoAOoQSIL8O6+goHsRaR7uwQX0YQVsKIFgAKAktZsiwkLluh2WFPN17WerruwMSgJ8ADZB+hjYpeahn8GChtz7zCPorhEnnuPkFAAAAAElFTkSuQmCC", 0);
    public static final byte[] DEFAULT_THUMBNAIL_128 = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAIAAAACAAgMAAAC+UIlYAAAAAXNSR0IB2cksfwAAAARnQU1BAACxjwv8YQUAAAAgY0hSTQAAeiYAAICEAAD6AAAAgOgAAHUwAADqYAAAOpgAABdwnLpRPAAAAAxQTFRFAAAAAAAA////nZ2detZtKgAAAAF0Uk5TAEDm2GYAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAA80lEQVRYw+3XTQ6EIAwF4IYlR+GUrOcUZFbNO+WYOIz8tGAMiibzlvJpAtIGiP65f+Db4wbhyeA7PRXEgROBVYHlDnAYBvwgYAXgLwe+WuN9wM0ELgKaCUgDBgJwCVjxDFAuMpC9OQdwtoINsO5m3BnEsnfjgWmCIAF/GLAGggqMBPK64B3g17REgBRAAqEJlmdIwRKuQNjaX0xIKx94rT8kCeegDqd/qwOoC2wPUDlQzCL9xLvYF8VEuNo4ZRfcA8JBgBMAy8CqgEcD0oGnqsXMAq4P/HRQnnKA60FdOX8gnuEmgO1ErlxyDIbfo7q3wSflA6NL5p69Qoa3AAAAAElFTkSuQmCC", 0);
    public static final String DEFAULT_DEVICE_NAME = "Geargrinder";
    public static final String DEFAULT_DEVICE_MODEL = "geargrinder automotive projection app";

    public static ServiceDiscoveryRequest getDefault() {
        return new ServiceDiscoveryRequest(
                DEFAULT_THUMBNAIL_32,
                DEFAULT_THUMBNAIL_64,
                DEFAULT_THUMBNAIL_128,
                DEFAULT_DEVICE_NAME,
                DEFAULT_DEVICE_MODEL
        );
    }

    public byte[] serialize() {
        return ProtoSerializer.serialize(
                new ProtoSerializer.ProtoVarData(1, thumbnail32()),
                new ProtoSerializer.ProtoVarData(2, thumbnail64()),
                new ProtoSerializer.ProtoVarData(3, thumbnail128()),
                new ProtoSerializer.ProtoVarData(4, deviceName().getBytes(StandardCharsets.UTF_8)),
                new ProtoSerializer.ProtoVarData(5, deviceModel().getBytes(StandardCharsets.UTF_8))
        );
    }
}
