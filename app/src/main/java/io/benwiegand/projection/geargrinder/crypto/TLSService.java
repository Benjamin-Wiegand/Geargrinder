package io.benwiegand.projection.geargrinder.crypto;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;

public class TLSService {
    private static final String TAG = TLSService.class.getSimpleName();

    private final SSLEngine sslEngine;
    private final SSLSession sslSession;

    private final ByteBuffer appRxBuffer;
    private final ByteBuffer appTxBuffer;
    private final ByteBuffer devRxBuffer;
    private final ByteBuffer devTxBuffer;

    private static final Object handshakeLock = new Object();

    private boolean handshakeComplete = false;


    public TLSService(TrustManager[] trustManagers, KeyManager[] keyManagers) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            Log.i(TAG, "ssl provider: " + sslContext.getProvider());

            sslContext.init(
                    keyManagers,
                    trustManagers,
                    SecureRandom.getInstanceStrong()
            );

            sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(false);  // phone is the server, headunit is the client
            sslEngine.setEnabledProtocols(new String[]{"TLSv1.2",});  // TLSv1.3 does not work, nor does TLSv1.1

            Log.d(TAG, "default cipher suites: " + Arrays.toString(sslEngine.getEnabledCipherSuites()));
            Log.d(TAG, "all cipher suites: " + Arrays.toString(sslEngine.getSupportedCipherSuites()));

            // TODO: if wireless is ever implemented, this needs to be removed since it is insecure.
            //       and also the certificate needs to be validated too.
            sslEngine.setEnabledCipherSuites(sslEngine.getSupportedCipherSuites());

            sslSession = sslEngine.getSession();

            appRxBuffer = ByteBuffer.allocateDirect(sslSession.getApplicationBufferSize());
            appTxBuffer = ByteBuffer.allocateDirect(sslSession.getApplicationBufferSize());
            devRxBuffer = ByteBuffer.allocateDirect(sslSession.getPacketBufferSize());
            devTxBuffer = ByteBuffer.allocateDirect(sslSession.getPacketBufferSize());

            Log.d(TAG, "engine class: " + sslEngine.getClass().getName());
            Log.d(TAG, "max ciphertext: " + sslSession.getPacketBufferSize());
            Log.d(TAG, "max plaintext: " + sslSession.getApplicationBufferSize());

        } catch (Throwable t) {
            Log.e(TAG, "fatal exception during SSL init", t);
            throw new RuntimeException(t);
        }
    }

    public int getMaxPlaintextSize(int maxCiphertextSize) {
        if (maxCiphertextSize > sslSession.getPacketBufferSize())
            return sslSession.getApplicationBufferSize();

        int maxOverhead = sslSession.getPacketBufferSize() - sslSession.getApplicationBufferSize();
        if (maxOverhead > maxCiphertextSize)
            return 0;

        return maxCiphertextSize - maxOverhead;
    }

    public boolean needsHandshake() {
        return !handshakeComplete;
    }

    public <T> T encrypt(byte[] input, int inOffset, int inLength, Function<ByteBuffer, T> outBufferConsumer) throws IOException {
        if (!handshakeComplete) throw new IllegalStateException("handshake must be completed before encrypting data");

        appTxBuffer.clear();
        devTxBuffer.clear();

        appTxBuffer.put(input, inOffset, inLength);
        appTxBuffer.flip();

        SSLEngineResult result = sslEngine.wrap(appTxBuffer, devTxBuffer);

        if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
            Log.e(TAG, "ssl engine closed");
            throw new IOException("ssl/tls session closed");
        } else if (result.getStatus() != SSLEngineResult.Status.OK) {
            // overflow is an error, input should not exceed expected size
            Log.e(TAG, "ssl engine result status = " + result.getStatus());
            throw new IOException("unexpected ssl engine result " + result.getStatus());
        }

        assert !appTxBuffer.hasRemaining();

        devTxBuffer.flip();
        return outBufferConsumer.apply(devTxBuffer);
    }

    public <T> T decrypt(byte[] input, int inOffset, int inLength, Function<ByteBuffer, T> outBufferConsumer) throws IOException {
        if (!handshakeComplete) throw new IllegalStateException("handshake must be completed before decrypting data");

        appRxBuffer.clear();
        devRxBuffer.clear();

        devRxBuffer.put(input, inOffset, inLength);
        devRxBuffer.flip();

        while (devRxBuffer.hasRemaining()) {
            SSLEngineResult result = sslEngine.unwrap(devRxBuffer, appRxBuffer);

            if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                Log.e(TAG, "ssl engine closed");
                throw new IOException("ssl/tls session closed");
            } else if (result.getStatus() != SSLEngineResult.Status.OK) {
                // headunits likely don't have more than 16 KiB of data to send anyway
                Log.e(TAG, "ssl engine result status = " + result.getStatus());
                throw new IOException("unexpected ssl engine result " + result.getStatus());
            }
        }

        appRxBuffer.flip();
        return outBufferConsumer.apply(appRxBuffer);
    }

    public void doHandshake(byte[] inBuffer, int offset, int length, Consumer<ByteBuffer> bufferSender) throws SSLException {
        synchronized (handshakeLock) {
            if (handshakeComplete) {
                Log.wtf(TAG, "ignoring doHandshake(): handshake already completed");
                return;
            }

            if (inBuffer != null) {
                devRxBuffer.put(inBuffer, offset, length);
                devRxBuffer.flip();
            }

            if (sslEngine.getHandshakeStatus() == NOT_HANDSHAKING) {
                Log.i(TAG, "starting handshake");
                sslEngine.beginHandshake();
            }

            while (true) {  // until it succeeds or explodes
                SSLEngineResult.HandshakeStatus hs = sslEngine.getHandshakeStatus();
                Log.v(TAG, "handshake status: " + hs.name());
                switch (hs) {
                    case NOT_HANDSHAKING -> {
                        Log.i(TAG, "handshake complete");
                        Log.d(TAG, "cipher suite: " + sslSession.getCipherSuite());
                        handshakeComplete = true;
                        return;
                    }
                    case NEED_WRAP -> {
                        devTxBuffer.clear();
                        sslEngine.wrap(ByteBuffer.allocate(0), devTxBuffer);
                        devTxBuffer.flip();
                        Log.d(TAG, "handshake out: " + devTxBuffer.remaining() + " bytes");
                        bufferSender.accept(devTxBuffer);
                        assert devTxBuffer.remaining() == 0;
                    }
                    case NEED_UNWRAP -> {
                        if (inBuffer == null) {
                            Log.d(TAG, "doHandshake() terminating: need more data");
                            return;
                        }
                        inBuffer = null;

                        while (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                            sslEngine.unwrap(devRxBuffer, appRxBuffer);
                        }

                        devRxBuffer.compact();
                    }
                    case NEED_TASK -> {
                        Runnable task;
                        while ((task = sslEngine.getDelegatedTask()) != null)
                            task.run();
                    }
                    default -> throw new AssertionError("unknown handshake status: " + hs);
                }
            }
        }
    }

}
