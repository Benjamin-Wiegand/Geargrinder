package io.benwiegand.projection.geargrinder.util;

import java.io.IOException;

import io.benwiegand.projection.geargrinder.coordinate.CoordinateTranslator;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;
import io.benwiegand.projection.geargrinder.shell.RootShell;

public class RootUtil {
    public static void simulateTouchEventRoot(RootShell rootShell, int displayId, TouchEvent event, CoordinateTranslator<TouchEvent.PointerLocation> translator) throws IOException {
        // TODO: this can be a lot better
        if (event.pointerLocations().length == 0) return;

        int x = translator.translateX(event.pointerLocations()[0]);
        int y = translator.translateY(event.pointerLocations()[0]);

        switch (event.action()) {
            case DOWN, UP, MOVE, CANCEL -> {
                rootShell.writeLine("input touchscreen -d " + displayId + " motionevent " + event.action().name() + " " + x + " " + y);
            }
            case OUTSIDE, POINTER_DOWN, POINTER_UP -> {
                // no multitouch (yet)
            }
        }
    }
}
