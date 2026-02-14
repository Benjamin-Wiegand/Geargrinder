package io.benwiegand.projection.geargrinder.privd.ipc;

import android.content.Context;
import android.hardware.input.InputManager;
import android.util.Log;
import android.view.MotionEvent;

import java.io.IOException;
import java.net.Socket;

import io.benwiegand.projection.libprivd.reflection.ReflectionException;
import io.benwiegand.projection.geargrinder.privd.reflected.ReflectedInputManager;
import io.benwiegand.projection.libprivd.data.SerializableMotionEvent;
import io.benwiegand.projection.libprivd.ipc.IPCConnection;
import io.benwiegand.projection.libprivd.ipc.IPCConstants;

public class AppIPCConnection extends IPCConnection {
    private static final String TAG = AppIPCConnection.class.getSimpleName();

    private final ReflectedInputManager rim;

    public AppIPCConnection(Socket socket, byte[] tokenA, byte[] tokenB, Context context) throws IOException {
        super(socket, tokenA, tokenB);
        InputManager im = context.getSystemService(InputManager.class);
        rim = new ReflectedInputManager(im);
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Reply onCommand(int command, byte[] data, int offset, int length) {
        return switch (command) {
            case IPCConstants.COMMAND_PING -> {
                Log.d(TAG, "ping");
                yield new Reply(IPCConstants.REPLY_SUCCESS);
            }
            case IPCConstants.COMMAND_INJECT_MOTION_EVENT -> {
                Log.d(TAG, "inject motion event");

                boolean result;
                try {
                    SerializableMotionEvent sme = SerializableMotionEvent.fromByteArray(data, offset, length);
                    MotionEvent event = sme.toMotionEvent();
                    result = rim.injectInputEvent(event, ReflectedInputManager.INJECT_MODE_ASYNC);
                } catch (ReflectionException e) {
                    Log.e(TAG, "reflection exception while injecting input event", e);
                    yield new Reply(IPCConstants.REPLY_FAILURE);
                }

                byte[] reply = new byte[1];
                reply[0] = (byte) (result ? 1 : 0);
                yield new Reply(IPCConstants.REPLY_SUCCESS, reply);
            }
            default -> {
                Log.wtf(TAG, "unhandled command: " + command);
                yield new Reply(IPCConstants.REPLY_FAILURE);
            }
        };
    }

    @Override
    protected void onClose() {
        // daemon has no reason to run without an app
        Log.e(TAG, "connection closed");
        System.exit(0);
    }
}
