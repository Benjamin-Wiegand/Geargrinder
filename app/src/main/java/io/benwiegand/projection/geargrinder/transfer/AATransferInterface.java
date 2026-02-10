package io.benwiegand.projection.geargrinder.transfer;

import java.io.IOException;

public interface AATransferInterface {

    /**
     * checks the status of the underlying connection
     * @return true if alive, false if not
     */
    boolean alive();

    /**
     * sends an entire AA frame over the underlying byte stream
     * @param buffer input data buffer
     * @param offset offset of input data
     * @param length length of input data
     * @throws IOException if the connection died
     */
    void sendFrame(byte[] buffer, int offset, int length) throws IOException;

    /**
     * reads an entire AA frame from the underlying byte stream, blocking if needed
     * @param buffer output
     * @return the number of bytes in the message
     * @throws IOException if the connection died
     */
    int readFrame(byte[] buffer) throws IOException;

    /**
     * closes the connection
     */
    void close() throws IOException;

}
