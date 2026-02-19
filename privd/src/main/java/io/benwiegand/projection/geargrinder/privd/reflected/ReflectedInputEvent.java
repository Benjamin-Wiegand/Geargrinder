package io.benwiegand.projection.geargrinder.privd.reflected;

import android.view.InputEvent;

import java.lang.reflect.Method;

import io.benwiegand.projection.geargrinder.privd.reflection.ReflectedObject;
import io.benwiegand.projection.geargrinder.privd.reflection.ReflectionException;

public class ReflectedInputEvent extends ReflectedObject {
    private final Method setDisplayId;

    public ReflectedInputEvent(InputEvent instance) {
        super(instance, InputEvent.class);
        setDisplayId = findMethod("setDisplayId", int.class);
    }

    public void setDisplayId(int displayId) throws ReflectionException {
        invokeMethodNoException(setDisplayId, displayId);
    }
}
