package io.benwiegand.projection.geargrinder.callback;

import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.ButtonEvent;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.RelativeEvent;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;

public interface InputEventListener {

    void onTouchEvent(TouchEvent event);

    void onButtonEvent(ButtonEvent event);

    void onRelativeEvent(RelativeEvent event);

}
