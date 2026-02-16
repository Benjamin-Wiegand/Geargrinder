package io.benwiegand.projection.geargrinder.video;

import android.view.Surface;

public interface FrameCopier {

    void init() throws Throwable;

    void destroy();

    Surface getInputSurface();

    int nextFrameNumber();
    int copyFrame();

}
