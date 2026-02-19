package io.benwiegand.projection.geargrinder.privd.reflection;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class ReflectedObject {
    private static final String TAG = ReflectedObject.class.getSimpleName();

    protected final Object instance;
    protected final Class<?> clazz;

    protected ReflectedObject(Object instance, Class<?> clazz) {
        this.instance = instance;
        this.clazz = clazz;
        assert instance == null || instance.getClass().equals(clazz);
    }

    public Object getRawInstance() {
        return instance;
    }

    protected static Class<?> findClass(String name) throws ReflectionException {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new ReflectionException(e);
        }
    }

    protected Method findMethod(String name, Class<?>... parameterTypes) {
        return findMethod(clazz, name, parameterTypes);
    }

    protected Object invokeMethodNoException(Method method, Object... args) throws ReflectionException {
        try {
            return invokeMethod(method, args);
        } catch (ReflectionException | RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new ReflectionException("unexpected exception thrown by " + method.getName(), t);
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

    protected static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "failed to find method " + name + " for class " + clazz.getName(), e);
            return null;
        }
    }

    protected static Object invokeStaticMethodNoException(Method method, Object... args) throws ReflectionException {
        try {
            return invokeStaticMethod(method, args);
        } catch (ReflectionException | RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new ReflectionException("unexpected exception thrown by " + method.getName(), t);
        }
    }

    protected static Object invokeStaticMethod(Method method, Object... args) throws Throwable {
        if (method == null) throw new ReflectionException("method not available");
        try {
            return method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new ReflectionException(e);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
