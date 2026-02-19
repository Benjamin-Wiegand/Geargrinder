package io.benwiegand.projection.geargrinder.privd.reflected;

import android.annotation.SuppressLint;
import android.content.Context;

import java.lang.reflect.Method;

import io.benwiegand.projection.libprivd.reflection.ReflectedObject;
import io.benwiegand.projection.libprivd.reflection.ReflectionException;

public class ReflectedActivityThread extends ReflectedObject {
    private static final String CLASS_NAME = "android.app.ActivityThread";

    private final Method getSystemContext;
    private final Method getApplicationThread;

    @SuppressLint("PrivateApi")
    public ReflectedActivityThread(Object instance) throws ReflectionException {
        super(instance, findClass(CLASS_NAME));
        getSystemContext = findMethod("getSystemContext");
        getApplicationThread = findMethod("getApplicationThread");
    }

    public Context getSystemContext() throws ReflectionException {
        return (Context) invokeMethodNoException(getSystemContext);
    }

    public ReflectedApplicationThread getApplicationThread() throws ReflectionException {
        Object result = invokeMethodNoException(getApplicationThread);
        return new ReflectedApplicationThread(result);
    }

    public static ReflectedActivityThread systemMain() throws ReflectionException {
        Class<?> clazz = findClass(CLASS_NAME);
        Method systemMain = findMethod(clazz, "systemMain");
        Object result = invokeStaticMethodNoException(systemMain);
        return new ReflectedActivityThread(result);
    }

}
