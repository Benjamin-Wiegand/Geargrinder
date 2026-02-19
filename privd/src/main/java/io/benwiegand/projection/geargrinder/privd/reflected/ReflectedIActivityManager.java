package io.benwiegand.projection.geargrinder.privd.reflected;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.benwiegand.projection.geargrinder.privd.reflection.ReflectedAidlInterface;
import io.benwiegand.projection.geargrinder.privd.reflection.ReflectionException;

public class ReflectedIActivityManager extends ReflectedAidlInterface {
    private static final String CLASS_NAME = "android.app.ActivityManagerNative";
    private static final String PACKAGE_NAME = "com.android.shell";

    private static final int USER_CURRENT = -2;

    private final Method startActivityAsUser;

    public ReflectedIActivityManager() throws ReflectionException {
        super(getService());
        Class<?> iApplicationThread = findClass("android.app.IApplicationThread");
        Class<?> profilerInfo = findClass("android.app.ProfilerInfo");

        startActivityAsUser = findMethod("startActivityAsUser", iApplicationThread, String.class, Intent.class, String.class, IBinder.class, String.class, int.class, int.class, profilerInfo, Bundle.class, int.class);
    }

    public int startActivityAsUser(String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int flags, Bundle options, int userId) throws ReflectionException {
        return (int) invokeMethodNoException(startActivityAsUser, null, callingPackage, intent, resolvedType, resultTo, resultWho, requestCode, flags, null, options, userId);
    }

    public int startActivity(Intent intent, Bundle options) throws ReflectionException {
        return startActivityAsUser(PACKAGE_NAME, intent, null, null, null, 0, 0, options, USER_CURRENT);
    }

    private static IInterface getService() throws ReflectionException {
        try {
            Class<?> clazz = findClass(CLASS_NAME);
            Method getter = clazz.getMethod("getDefault");
            getter.setAccessible(true);
            return (IInterface) getter.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ReflectionException(e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) throw re;
            throw new ReflectionException("unexpected exception while getting instance of " + CLASS_NAME, e.getTargetException());
        }
    }
}
