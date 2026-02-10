package io.benwiegand.projection.geargrinder.projection;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;

import io.benwiegand.projection.geargrinder.callback.InputEventListener;
import io.benwiegand.projection.geargrinder.coordinate.CoordinateTranslator;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;

public class InputEventMuxer implements InputEventListener {

    public static class Destination {
        private final CoordinateTranslator<TouchEvent.PointerLocation> translator;
        private final InputEventListener inputEventListener;
        private final Rect bounds = new Rect();
        private boolean enabled = true;

        private Destination(InputEventListener listener) {
            translator = CoordinateTranslator.create(
                    x -> x - bounds.left,
                    y -> y - bounds.top,
                    TouchEvent.PointerLocation.class
            );
            inputEventListener = listener;
        }

        public Rect getBounds() {
            return bounds;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private final List<Destination> destinations = new ArrayList<>();

    private final InputEventListener rootListener;


    public InputEventMuxer(InputEventListener rootListener) {
        this.rootListener = rootListener;
    }


    public Destination addDestination(InputEventListener listener) {
        Destination destination = new Destination(listener);
        destinations.add(destination);
        return destination;
    }

    public void removeDestination(Destination destination) {
        destinations.remove(destination);
    }

    @Override
    public void onTouchEvent(TouchEvent event, CoordinateTranslator<TouchEvent.PointerLocation> translator) {
        // TODO: multi-touch
        int x = translator.translateX(event.pointerLocations()[0]);
        int y = translator.translateY(event.pointerLocations()[0]);

        // TODO: pointer ownership
        for (Destination destination : destinations) {
            if (!destination.bounds.contains(x, y)) continue;
            if (!destination.enabled) continue;
            destination.inputEventListener.onTouchEvent(event, translator.chain(destination.translator));
            return;
        }

        rootListener.onTouchEvent(event, translator);
    }
}
