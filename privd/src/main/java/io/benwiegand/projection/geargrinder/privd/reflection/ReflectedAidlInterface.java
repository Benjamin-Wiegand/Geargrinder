package io.benwiegand.projection.geargrinder.privd.reflection;

import android.os.IInterface;

public abstract class ReflectedAidlInterface extends ReflectedObject {
    protected ReflectedAidlInterface(IInterface instance) {
        super(instance, instance.getClass());
    }
}
