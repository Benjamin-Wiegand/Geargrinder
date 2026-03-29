package io.benwiegand.projection.geargrinder.privileged;

import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN;

import android.content.Context;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.benwiegand.projection.geargrinder.IShizukuUserService;
import io.benwiegand.projection.geargrinder.service.GeargrinderServiceConnector;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

public class ShizukuPrivdLauncher extends PrivdLauncher implements GeargrinderServiceConnector.ConnectionListener {
    private static final String TAG = ShizukuPrivdLauncher.class.getSimpleName();

    private static final long SHIZUKU_CONNECTION_TIMEOUT = 20000;

    private final Object lock = new Object();

    private final GeargrinderServiceConnector connector;

    private static File requireExternalFilesDir(Context context) {
        File file = context.getExternalFilesDir(null);
        if (file == null) throw new UnsupportedOperationException("an external files directory is required");
        return file;
    }

    public ShizukuPrivdLauncher(Context context) {
        super(
                context,
                requireExternalFilesDir(context).toPath().resolve(PRIVD_FILE_NAME).toFile(),
                requireExternalFilesDir(context).toPath().resolve(LAUNCH_SCRIPT_FILE_NAME).toFile(),
                requireExternalFilesDir(context).toPath().resolve(TOKEN_FILE_NAME).toFile()
        );

        connector = new GeargrinderServiceConnector(TAG, context, this);
        Shizuku.addBinderReceivedListenerSticky(this::onBinderReceived);
    }

    @Override
    public void destroy() {
        Shizuku.removeBinderReceivedListener(this::onBinderReceived);
        connector.getShizukuUserService().ifPresent(this::killProcess);
        connector.destroy();
    }

    private void onBinderReceived() {
        Log.i(TAG, "Shizuku binder received");
        checkShizukuPermission()
                .filter(p -> p)
                .ifPresent(permission -> connector.bindShizukuUserService());
    }

    @Override
    public void onShizukuUserServiceConnected(IShizukuUserService service) {
        Log.i(TAG, "Shizuku user service connected");
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    private IShizukuUserService waitForShizukuUserService() throws InterruptedException, TimeoutException {
        synchronized (lock) {
            Optional<IShizukuUserService> serviceOptional = connector.getShizukuUserService();
            if (serviceOptional.isPresent()) return serviceOptional.get();

            Log.i(TAG, "waiting for Shizuku user service");
            lock.wait(SHIZUKU_CONNECTION_TIMEOUT);
            serviceOptional = connector.getShizukuUserService();

            return serviceOptional
                    .orElseThrow(() -> new TimeoutException("Shizuku user service not connected before timeout"));
        }
    }

    @Override
    public void launch() throws IOException, RemoteException, InterruptedException, TimeoutException {
        init();

        Log.i(TAG, "launching privd as shizuku");

        new Thread(() -> {
            try {
                IShizukuUserService userService = waitForShizukuUserService();
                Log.i(TAG, "shizuku connected");
                Log.d(TAG, "shizuku version: " + Shizuku.getVersion());
                Log.d(TAG, "shizuku UID: " + Shizuku.getUid());
                Log.d(TAG, "SELinux context: " + Shizuku.getSELinuxContext());
                executeDaemon(userService);
            } catch (Throwable t) {
                Log.e(TAG, "shizuku privd launch failed", t);
                onError(t);
            }
        }).start();
    }

    private void executeDaemon(IShizukuUserService userService) throws RemoteException {
        Log.v(TAG, "requesting privd execution");
        userService.execPrivd(launchScriptFile.getAbsolutePath(), Map.of(
                ENV_TOKEN, Base64.encodeToString(token, Base64.NO_WRAP)
        ));
    }

    private void killProcess(IShizukuUserService userService) {
        Log.d(TAG, "requesting to kill privd shizuku process");
        try {
            userService.killPrivd();
        } catch (RemoteException e) {
            Log.w(TAG, "failed to kill process", e);
        }
    }

    private Optional<IShizukuService> getShizukuService() {
        return Optional.ofNullable(IShizukuService.Stub.asInterface(Shizuku.getBinder()));
    }

    public Optional<Boolean> checkShizukuPermission() {
        return getShizukuService()
                .flatMap(shizuku -> {
                    try {
                        return Optional.of(shizuku.checkSelfPermission());
                    } catch (RemoteException e) {
                        Log.e(TAG, "remote exception while checking shizuku permission", e);
                        return Optional.empty();
                    }
                });
    }
}
