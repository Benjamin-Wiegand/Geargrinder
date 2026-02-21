package io.benwiegand.projection.libprivd.ipc;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public abstract class ReflectingBinder extends Binder {
    private static final String TAG = ReflectingBinder.class.getSimpleName();

    private static final int METHOD_INVOCATION_TRANSACTION = FIRST_CALL_TRANSACTION;

    private final Map<Integer, Method> methodLookup;

    public ReflectingBinder(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        methodLookup = new HashMap<>(methods.length);
        for (Method method : methods)
            methodLookup.put(getMethodId(method), method);
    }

    protected abstract void checkCaller();

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        checkCaller();
        Binder.clearCallingIdentity();

        if (code != METHOD_INVOCATION_TRANSACTION)
            return super.onTransact(code, data, reply, flags);

        Method method = methodLookup.get(data.readInt());
        if (method == null) throw new AssertionError("no method found for provided id");

        Object[] methodParams = new Object[method.getParameterCount()];
        for (int i = 0; i < method.getParameterCount(); i++)
            methodParams[i] = data.readValue(getClass().getClassLoader());

        try {
            Object result = method.invoke(this, methodParams);
            reply.writeNoException();
            reply.writeValue(result);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof Exception exception) {
                reply.writeException(exception);
            } else {
                reply.writeException(new Exception(e.getTargetException()));
            }
        }

        return true;
    }

    public static <T extends IInterface> IInterface proxyInterface(IBinder binder, Class<T> clazz) {
        Method asBinder = null;
        try {
            asBinder = IInterface.class.getDeclaredMethod("asBinder");
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "unable to find asBinder() method in class: " + clazz, e);
        }

        int asBinderMethodId = asBinder != null ? getMethodId(asBinder) : -1;

        return (IInterface) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{ clazz }, (proxy, method, args) -> {
            int methodId = getMethodId(method);
            if (methodId == asBinderMethodId) return binder;

            // binder calls must throw RemoteException
            assert Arrays.stream(method.getExceptionTypes()).anyMatch(Predicate.isEqual(RemoteException.class));

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {

                data.writeInt(methodId);

                if (args != null) {
                    for (Object arg : args)
                        data.writeValue(arg);
                }

                if (!binder.transact(METHOD_INVOCATION_TRANSACTION, data, reply, 0))
                    throw new AssertionError("transact() returned false");

                reply.readException();
                return reply.readValue(clazz.getClassLoader());
            } finally {
                data.recycle();
                reply.recycle();
            }
        });
    }

    private static int getMethodId(Method method) {
        StringBuilder sb = new StringBuilder(method.getName());
        for (Class<?> paramType : method.getParameterTypes()) sb
                .append(";")
                .append(paramType.getName());
        return sb.toString().hashCode();
    }
}
