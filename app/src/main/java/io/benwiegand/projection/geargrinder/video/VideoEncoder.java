package io.benwiegand.projection.geargrinder.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;


import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoEncoder {
    private static final String TAG = VideoEncoder.class.getSimpleName();
    
    private static final boolean LOG_FRAME_SIZE_DEBUG = true;

    private static final int I_FRAME_INTERVAL = 5;

    private static final int BITRATE_MIN = 100000;    // 100 kb/s

    // fraction of estimated max bitrate to target
    private static final float BITRATE_MARGIN_MAX = 0.5f;   // aim for half the buffer

    // multiplier for bitrate when a frame is dropped for being too large
    private static final float BITRATE_DROP_MULTIPLIER = 0.7f;

    // multiplier for bitrate recovery (once per I frame)
    private static final float BITRATE_RECOVERY_MULTIPLIER = 1.1f;


    private MediaCodec encoder = null;
    private Surface hardwareSurface = null;

    private final int width;
    private final int height;
    private final int maxFrameRate;
    private final int maxFrameSize;

    private int bitrate;
    private float targetBitrateMargin = BITRATE_MARGIN_MAX;

    private boolean dropUntilSync = false;

    private final MediaCodec.BufferInfo bufferInfo;


    public VideoEncoder(int width, int height, int maxFrameRate, int maxFrameSize) {
        this.width = width;
        this.height = height;
        this.maxFrameRate = maxFrameRate;
        this.maxFrameSize = maxFrameSize;

        bufferInfo = new MediaCodec.BufferInfo();
        bitrate = getTargetBitrate();
    }

    public void init() throws IOException {
        // TODO: fix slow motion bug on some phones at 30 fps
        //       I think I need to use a SurfaceTexture

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
//        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height);   // TODO: figure out how to determine support for this
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, maxFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);

        format.setInteger(MediaFormat.KEY_LATENCY, 1);
        format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100000); // TODO
        format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            format.setInteger(MediaFormat.KEY_VIDEO_QP_P_MIN, 30);
            format.setInteger(MediaFormat.KEY_VIDEO_QP_P_MAX, 100);
//
            format.setInteger(MediaFormat.KEY_VIDEO_QP_B_MIN, 30);
            format.setInteger(MediaFormat.KEY_VIDEO_QP_B_MAX, 100);
//
            format.setInteger(MediaFormat.KEY_VIDEO_QP_I_MIN, 40);
            format.setInteger(MediaFormat.KEY_VIDEO_QP_I_MAX, 100);
        }

        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo ci : codecList.getCodecInfos()) {
            Log.i(TAG, "codec: " + ci.getName());
        }

        String codecName = codecList.findEncoderForFormat(format);
        if (codecName == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.w(TAG, "trying cbr fd bitrate mode");
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD);
            codecName = codecList.findEncoderForFormat(format);
        }
        if (codecName == null) {
            Log.w(TAG, "trying cq bitrate mode");
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
            codecName = codecList.findEncoderForFormat(format);
        }
        if (codecName == null) {
            Log.w(TAG, "trying vbr bitrate mode");
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            codecName = codecList.findEncoderForFormat(format);
        }
        if (codecName == null) {
            Log.e(TAG, "couldn't find encoder");
            throw new RuntimeException("failed to find a suitable encoder");
        }

//        String codecName = "c2.android.avc.encoder";
//        Log.i(TAG, "forcing encoder: " + codecName);

//        String codecName = "OMX.Exynos.AVC.Encoder";
//        Log.i(TAG, "forcing encoder: " + codecName);

//        String codecName = "OMX.MTK.VIDEO.ENCODER.AVC";
//        Log.i(TAG, "forcing encoder: " + codecName);

//        String codecName = "c2.mtk.avc.encoder";
//        Log.i(TAG, "forcing encoder: " + codecName);

        Log.i(TAG, "found encoder: " + codecName);
        encoder = MediaCodec.createByCodecName(codecName);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        hardwareSurface = encoder.createInputSurface();
        encoder.start();
    }

    public void destroy() {
        if (encoder != null) {
            encoder.stop();
            encoder.release();
        }
    }

    public Surface getInputSurface() {
        return hardwareSurface;
    }

    private int getTargetBitrate() {
        int targetBitrate = (int) (maxFrameSize * maxFrameRate * targetBitrateMargin);
        if (targetBitrate < BITRATE_MIN) {
            Log.w(TAG, "hit min bitrate of " + BITRATE_MIN);
            return BITRATE_MIN;
        }
        return targetBitrate;
    }

    private void updateBitrate(boolean requestSync) {
        int targetBitrate = getTargetBitrate();
        if (targetBitrate == bitrate) return;

        Bundle params = new Bundle();

        Log.i(TAG, "updating bitrate: " + bitrate + " -> " + targetBitrate);
        params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, targetBitrate);

        if (requestSync) {
            Log.v(TAG, "requesting I frame");
            params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
        }

        encoder.setParameters(params);
        bitrate = targetBitrate;
    }

    private void onOversizedFrame() {
        // any new P frames should be discarded to avoid ghost UI elements and other jank
        dropUntilSync = true;

        // need to get a usable I frame ASAP, video will remain frozen until then
        targetBitrateMargin *= BITRATE_DROP_MULTIPLIER;
        updateBitrate(true);
    }

    private void onKeyFrame() {
        dropUntilSync = false;

        // recover bitrate
        if (targetBitrateMargin < BITRATE_MARGIN_MAX) {
            targetBitrateMargin *= BITRATE_RECOVERY_MULTIPLIER;
            if (targetBitrateMargin > BITRATE_MARGIN_MAX) targetBitrateMargin = BITRATE_MARGIN_MAX;
            updateBitrate(false);
        }

    }

    public enum FrameError {
        NO_ERROR,
        NO_FRAME,       // nothing to output
        TRY_AGAIN,      // something changed, next frame should probably work
        DROP,           // drop this frame
        FAILURE,        // something unexpected happened
        END_OF_STREAM,
    }

    public static class FrameResult {
        public int length = 0;
        public long timestamp = 0;
        public FrameError error = FrameError.NO_ERROR;
    }

    public void getFrame(FrameResult result, byte[] buffer, int offset, long timeoutUs) {

        int index = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
        if (index < 0) {
            switch (index) {
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.w(TAG, "error: INFO_OUTPUT_FORMAT_CHANGED");
                    result.error = FrameError.TRY_AGAIN;
                }
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    Log.w(TAG, "error: INFO_OUTPUT_BUFFERS_CHANGED");
                    result.error = FrameError.TRY_AGAIN;
                }
                case MediaCodec.INFO_TRY_AGAIN_LATER ->
                    // just means there's no frame yet
                    result.error = FrameError.NO_FRAME;
                default -> {
                    Log.e(TAG, "unexpected error: " + index);
                    result.error = FrameError.FAILURE;
                }
            }
            return;
        }

        ByteBuffer encoded = encoder.getOutputBuffer(index);
        if (encoded == null) {
            Log.wtf(TAG, "got null output buffer"); // this shouldn't happen
            result.error = FrameError.FAILURE;
            return;
        }

        try {
            boolean isFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0;
            boolean isKeyFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
            boolean isEOS = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

            
            if (isKeyFrame && LOG_FRAME_SIZE_DEBUG) {
                Log.v(TAG, "I frame size: " + bufferInfo.size);
            }

            if (bufferInfo.size > maxFrameSize) {
                if (isKeyFrame || !dropUntilSync) {
                    Log.e(TAG, "frame too large for output buffer: " + bufferInfo.size + " / " + (buffer.length - offset));
                    onOversizedFrame();
                }
                result.error = FrameError.DROP;
                return;
            }
            
            if (isKeyFrame) {
                onKeyFrame();
            } else if (dropUntilSync && isFrame) {
                result.error = FrameError.DROP;
                return;
            }

            if (isEOS) {
                Log.i(TAG, "end of stream");
                result.error = FrameError.END_OF_STREAM;
                return;
            } else if (bufferInfo.size == 0) {    // this isn't supposed to happen
                Log.e(TAG, "buffer size is 0, but not end of stream");
                result.error = FrameError.FAILURE;
                return;
            }

            encoded.position(bufferInfo.offset);
            encoded.limit(bufferInfo.offset + bufferInfo.size);
            encoded.get(buffer, offset, bufferInfo.size);

            result.error = FrameError.NO_ERROR;
            result.length = bufferInfo.size;
            if (isFrame) result.timestamp = bufferInfo.presentationTimeUs;
        } finally {
            encoder.releaseOutputBuffer(index, false);
        }
    }
}
