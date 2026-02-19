package io.benwiegand.projection.geargrinder.privd.reflected;

import android.content.ComponentName;
import android.os.IBinder;

import java.lang.reflect.Method;

import io.benwiegand.projection.geargrinder.privd.reflection.ReflectedInterface;
import io.benwiegand.projection.geargrinder.privd.reflection.ReflectionException;

public abstract class ReflectedIServiceConnection extends ReflectedInterface {
    private static final String CLASS_NAME = "android.app.IServiceConnection";

    protected ReflectedIServiceConnection() throws ReflectionException {
        super(findClass(CLASS_NAME));
    }

    public abstract void connected(ComponentName name, IBinder service, boolean dead);

    @Override
    protected Object onInvocation(Object instance, Method method, Object[] args) {
        if (method.getName().equals("connected")) {
            ComponentName name = (ComponentName) args[0];
            IBinder service = (IBinder) args[1];
            boolean dead = (boolean) args[2];
            connected(name, service, dead);
        }

        return null;
    }
}
