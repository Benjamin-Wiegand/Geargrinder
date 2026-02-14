package io.benwiegand.projection.geargrinder.privd.reflected;

import android.annotation.SuppressLint;
import android.hardware.input.InputManager;
import android.view.InputEvent;

import java.lang.reflect.Method;

import io.benwiegand.projection.libprivd.reflection.ReflectedObject;
import io.benwiegand.projection.libprivd.reflection.ReflectionException;

public class ReflectedInputManager extends ReflectedObject {
    public static final int INJECT_MODE_ASYNC = 0;

    private final Method injectInputEvent;

    @SuppressLint("DiscouragedPrivateApi")
    public ReflectedInputManager(InputManager instance) {
        super(instance, InputManager.class);
        injectInputEvent = findMethod("injectInputEvent", InputEvent.class, int.class);
    }

    public boolean injectInputEvent(InputEvent event, int mode) throws ReflectionException {
        return (boolean) invokeMethodNoException(injectInputEvent, event, mode);
    }

}
