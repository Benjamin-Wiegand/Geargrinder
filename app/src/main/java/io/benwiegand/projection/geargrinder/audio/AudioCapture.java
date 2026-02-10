package io.benwiegand.projection.geargrinder.audio;

public interface AudioCapture {

    enum Error {
        NO_ERROR,
        TRY_AGAIN,
        FAILURE,
        END_OF_STREAM
    }

    class Result {
        public Error error;
        public int length;
        public long timestamp;
        public boolean silent;

    }

    void begin();
    void destroy();

    void nextBuffer(Result result, byte[] buffer, int offset, int length);

}
