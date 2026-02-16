package io.benwiegand.projection.geargrinder.video;

import android.view.Surface;

/**
 * fake frame copier that just passes the output surface through to the input
 */
public class FramePassthrough implements FrameCopier {

    private final Surface surface;
    private int frameCounter;

    public FramePassthrough(Surface surface) {
        this.surface = surface;
    }

    @Override
    public void init() throws Throwable {
        // nothing to do
    }

    @Override
    public void destroy() {
        // nothing to do
    }

    @Override
    public Surface getInputSurface() {
        return surface;
    }

    @Override
    public int nextFrameNumber() {
        return frameCounter;
    }

    @Override
    public int copyFrame() {
        return frameCounter++;
    }
}
