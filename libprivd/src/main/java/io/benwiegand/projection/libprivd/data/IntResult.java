package io.benwiegand.projection.libprivd.data;

import android.os.Parcel;
import android.os.Parcelable;

public class IntResult implements Parcelable {
    private final int result;

    public IntResult(int result) {
        this.result = result;
    }

    protected IntResult(Parcel in) {
        result = in.readInt();
    }

    public int getResult() {
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(result);
    }

    public static final Creator<IntResult> CREATOR = new Creator<>() {
        @Override
        public IntResult createFromParcel(Parcel in) {
            return new IntResult(in);
        }

        @Override
        public IntResult[] newArray(int size) {
            return new IntResult[size];
        }
    };
}
