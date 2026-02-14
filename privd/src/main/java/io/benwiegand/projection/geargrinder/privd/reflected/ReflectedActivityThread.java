package io.benwiegand.projection.geargrinder.privd.reflected;

import android.annotation.SuppressLint;
import android.content.Context;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.benwiegand.projection.libprivd.reflection.ReflectedObject;
import io.benwiegand.projection.libprivd.reflection.ReflectionException;

public class ReflectedActivityThread extends ReflectedObject {
    private static final String CLASS_NAME = "android.app.ActivityThread";

    private final Method getSystemContext;

    @SuppressLint("PrivateApi")
    public ReflectedActivityThread() throws ReflectionException {
        super(createInstance(), findClass(CLASS_NAME));
        getSystemContext = findMethod("getSystemContext");
    }

    public Context getSystemContext() throws ReflectionException {
        return (Context) invokeMethodNoException(getSystemContext);
    }

    private static Object createInstance() throws ReflectionException {
        try {
            Class<?> clazz = findClass(CLASS_NAME);
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            throw new ReflectionException(e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) throw re;
            throw new ReflectionException("unexpected exception while constructing " + CLASS_NAME, e.getTargetException());
        }
    }
}
