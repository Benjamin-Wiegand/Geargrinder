package io.benwiegand.projection.geargrinder.privd.reflected;

import io.benwiegand.projection.geargrinder.privd.reflection.ReflectedObject;
import io.benwiegand.projection.geargrinder.privd.reflection.ReflectionException;

public class ReflectedApplicationThread extends ReflectedObject {
    private static final String CLASS_NAME = "android.app.ActivityThread$ApplicationThread";

    protected ReflectedApplicationThread(Object instance) throws ReflectionException {
        super(instance, findClass(CLASS_NAME));
    }
}
