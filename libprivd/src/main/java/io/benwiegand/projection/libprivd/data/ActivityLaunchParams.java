package io.benwiegand.projection.libprivd.data;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;

public class ActivityLaunchParams implements Parcelable {

    private final String packageName;
    private final String className;
    private final int displayId;


    public ActivityLaunchParams(String packageName, String className, int displayId) {
        this.packageName = packageName;
        this.className = className;
        this.displayId = displayId;
    }

    public ActivityLaunchParams(ComponentName componentName, int displayId) {
        this(componentName.getPackageName(), componentName.getClassName(), displayId);
    }

    protected ActivityLaunchParams(Parcel in) {
        packageName = in.readString();
        className = in.readString();
        displayId = in.readInt();
    }

    public ComponentName getComponent() {
        return new ComponentName(getPackageName(), getClassName());
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public int getDisplayId() {
        return displayId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeString(className);
        dest.writeInt(displayId);
    }

    public static final Creator<ActivityLaunchParams> CREATOR = new Creator<>() {
        @Override
        public ActivityLaunchParams createFromParcel(Parcel in) {
            return new ActivityLaunchParams(in);
        }

        @Override
        public ActivityLaunchParams[] newArray(int size) {
            return new ActivityLaunchParams[size];
        }
    };

    @Override
    public String toString() {
        return "ActivityLaunchParams{" +
                "packageName='" + packageName + '\'' +
                ", className='" + className + '\'' +
                ", displayId=" + displayId +
                '}';
    }
}
