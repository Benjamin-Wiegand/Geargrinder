package io.benwiegand.projection.libprivd.reflection;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public abstract class ReflectedInterface extends ReflectedObject {

    protected ReflectedInterface(Class<?> clazz) {
        super(null, clazz);
    }

    protected abstract Object onInvocation(Object instance, Method method, Object[] args);

    public Object asInterface() {
        return Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{ clazz }, this::onInvocation);
    }
}
