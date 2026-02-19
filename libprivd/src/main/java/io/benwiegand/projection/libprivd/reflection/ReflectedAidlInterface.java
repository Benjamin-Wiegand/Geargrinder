package io.benwiegand.projection.libprivd.reflection;

import android.os.IInterface;

public abstract class ReflectedAidlInterface extends ReflectedObject {
    protected ReflectedAidlInterface(IInterface instance) {
        super(instance, instance.getClass());
    }
}
