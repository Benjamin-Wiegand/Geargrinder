package io.benwiegand.projection.libprivd.reflection;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class ReflectedObject {
    private static final String TAG = ReflectedObject.class.getSimpleName();

    protected final Object instance;
    private final Class<?> clazz;

    protected ReflectedObject(Object instance, Class<?> clazz) {
        this.instance = instance;
        this.clazz = clazz;
        assert instance.getClass().equals(clazz);
    }

    protected static Class<?> findClass(String name) throws ReflectionException {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new ReflectionException(e);
        }
    }

    protected Method findMethod(String name, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "failed to find method " + name + " for class " + clazz.getName(), e);
            return null;
        }
    }

    protected Object invokeMethodNoException(Method method, Object... args) throws ReflectionException {
        if (method == null) throw new ReflectionException("method not available");
        try {
            return method.invoke(instance, args);
        } catch (IllegalAccessException e) {
            throw new ReflectionException(e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) throw re;
            throw new ReflectionException("unexpected exception thrown by " + method.getName(), e.getTargetException());
        }
    }

    protected Object invokeMethod(Method method, Object... args) throws Throwable {
        if (method == null) throw new ReflectionException("method not available");
        try {
            return method.invoke(instance, args);
        } catch (IllegalAccessException e) {
            throw new ReflectionException(e);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
