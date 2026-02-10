package io.benwiegand.projection.geargrinder.callback;

import io.benwiegand.projection.geargrinder.coordinate.CoordinateTranslator;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;

public interface InputEventListener {

    void onTouchEvent(TouchEvent event, CoordinateTranslator<TouchEvent.PointerLocation> translator);

}
