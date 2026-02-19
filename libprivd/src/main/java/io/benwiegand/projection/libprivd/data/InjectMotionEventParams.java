package io.benwiegand.projection.libprivd.data;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.MotionEvent;

public class InjectMotionEventParams implements Parcelable {

    private final MotionEvent event;
    private final int displayId;

    public InjectMotionEventParams(MotionEvent event, int displayId) {
        this.event = event;
        this.displayId = displayId;
    }

    public InjectMotionEventParams(MotionEvent event) {
        this.event = event;
        this.displayId = -1;
    }

    protected InjectMotionEventParams(Parcel in) {
        event = in.readParcelable(MotionEvent.class.getClassLoader());
        displayId = in.readInt();
    }

    public MotionEvent getEvent() {
        return event;
    }

    public boolean isDisplayIdSet() {
        return displayId < 0;
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
        dest.writeParcelable(event, flags);
        dest.writeInt(displayId);
    }

    public static final Creator<InjectMotionEventParams> CREATOR = new Creator<>() {
        @Override
        public InjectMotionEventParams createFromParcel(Parcel in) {
            return new InjectMotionEventParams(in);
        }

        @Override
        public InjectMotionEventParams[] newArray(int size) {
            return new InjectMotionEventParams[size];
        }
    };

    @Override
    public String toString() {
        return "InjectMotionEventParams{" +
                "event=" + event +
                ", displayId=" + displayId +
                '}';
    }
}
