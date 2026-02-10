package io.benwiegand.projection.geargrinder.channel;

import static android.media.AudioAttributes.USAGE_ALARM;
import static io.benwiegand.projection.geargrinder.message.AAFrame.COMMAND_ID_LENGTH;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.*;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.hexDump;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.readUInt16;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeUInt16;

import android.content.Context;
import android.media.AudioPlaybackCaptureConfiguration;
import android.util.Log;

import java.util.Arrays;

import javax.net.ssl.SSLException;

import io.benwiegand.projection.geargrinder.ConnectionService;
import io.benwiegand.projection.geargrinder.audio.AudioRecordCapture;
import io.benwiegand.projection.geargrinder.crypto.TLSService;
import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.callback.MessageListener;
import io.benwiegand.projection.geargrinder.projection.ProjectionService;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.AudioChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.ChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.InputChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.writable.ServiceDiscoveryRequest;
import io.benwiegand.projection.geargrinder.proto.data.readable.ServiceDiscoveryResponse;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.VideoChannelMeta;
import io.benwiegand.projection.geargrinder.callback.ControlListener;

public class ControlChannel implements MessageListener {
    private static final String TAG = ControlChannel.class.getSimpleName();

    private static final int VERSION_CODE_MAJOR = 1;
    private static final int VERSION_CODE_MINOR = 7;

    private final ConnectionService.ServiceBinder connectionServiceBinder;
    private final MessageBroker mb;
    private final TLSService tlsService;

    private final MessageBroker.MessageSendParameters unencryptedParams;
    private final MessageBroker.MessageSendParameters encryptedParams;

    private final ControlListener controlListener;

    private final ProjectionService projectionService;
    private VideoChannel videoChannel = null;
    private AudioChannel mediaAudioChannel = null;
    private InputChannel inputChannel = null;

    private VideoChannelMeta videoChannelMeta = null;
    private AudioChannelMeta mediaAudioChannelMeta = null;
    private InputChannelMeta inputChannelMeta = null;


    public ControlChannel(Context context, MessageBroker mb, TLSService tlsService, ControlListener controlListener, ConnectionService.ServiceBinder connectionServiceBinder) {
        this.connectionServiceBinder = connectionServiceBinder;
        this.mb = mb;
        this.tlsService = tlsService;
        this.controlListener = controlListener;
        projectionService = new ProjectionService(context);

        unencryptedParams = new MessageBroker.MessageSendParameters(CHANNEL_CONTROL, false, false);
        encryptedParams = new MessageBroker.MessageSendParameters(CHANNEL_CONTROL, true, false);
    }

    public void destroy() {
        projectionService.destroy();
        if (videoChannel != null) videoChannel.destroy();
        if (mediaAudioChannel != null) mediaAudioChannel.destroy();
        if (inputChannel != null) inputChannel.destroy();
    }

    private void handleServiceDiscoveryResponse(ServiceDiscoveryResponse response) {
        controlListener.onCarNameDiscovered(response.friendlyName());

        for (ChannelMeta channelMeta : response.channelMetadata()) switch (channelMeta) {
            case VideoChannelMeta vcm -> {
                if (videoChannelMeta != null) Log.w(TAG, "multiple video channels detected.");  // this maybe won't happen
                videoChannelMeta = vcm;
                Log.d(TAG, "found video channel: " + videoChannelMeta);
                Log.d(TAG, " - presets: " + Arrays.toString(videoChannelMeta.presets()));
            }
            case AudioChannelMeta acm -> {
                if (acm.audioType() != AudioChannelMeta.AudioType.MEDIA) {
                    Log.d(TAG, "found audio channel: " + acm);
                    continue;
                }
                if (mediaAudioChannelMeta != null) Log.w(TAG, "multiple media audio channels detected.");   // this probably won't happen
                mediaAudioChannelMeta = acm;
                Log.d(TAG, "found media audio channel: " + mediaAudioChannelMeta);
                Log.d(TAG, " - presets: " + Arrays.toString(mediaAudioChannelMeta.presets()));
            }
            case InputChannelMeta icm -> {
                if (inputChannelMeta != null) Log.w(TAG, "multiple input channels detected.");  // this probably won't happen
                inputChannelMeta = icm;
                Log.d(TAG, "found input channel: " + inputChannelMeta);
                if (!inputChannelMeta.hasTouchScreen()) Log.w(TAG, "no touch screen found");
            }
            case null -> {}
            default -> Log.d(TAG, "found channel: " + channelMeta);
        }

        if (videoChannelMeta == null) Log.w(TAG, "can't start video: no video channel");
        if (mediaAudioChannelMeta == null) Log.w(TAG, "can't start audio: no media audio channel");
        if (inputChannelMeta == null) Log.w(TAG, "can't start input: no input channel");
    }

    @Override
    public void onMessage(int channelId, int flags, byte[] buffer, int payloadOffset, int payloadLength) {
        if (payloadLength < COMMAND_ID_LENGTH) {
            Log.wtf(TAG, "message payload too small!", new RuntimeException());
            return;
        }

        int command = readUInt16(buffer, payloadOffset);
        switch (command) {
            case CMD_PING_REQUEST -> {
                Log.d(TAG, "ping!");
                mb.sendMessage(unencryptedParams, CMD_PING_RESPONSE);
            }

            case CMD_VERSION_REQUEST -> {
                Log.d(TAG, "version request: " + hexDump(buffer, payloadOffset, payloadLength));

                // TODO: rework
                if (payloadLength >= COMMAND_ID_LENGTH + 2) {
                    int major = readUInt16(buffer, COMMAND_ID_LENGTH);
                    int minor = readUInt16(buffer, COMMAND_ID_LENGTH + 2);
                    Log.v(TAG, "headunit version code: " + major + "." + minor);
                }

                // TODO: rework
                int i = 0;
                byte[] payload = new byte[8];
                i += writeUInt16(CMD_VERSION_RESPONSE, payload, i);
                i += writeUInt16(VERSION_CODE_MAJOR, payload, i);
                i += writeUInt16(VERSION_CODE_MINOR, payload, i);
                i += writeUInt16(0, payload, i);   // version code status. I assume this is for negotiation. (todo)
                mb.sendMessage(unencryptedParams, payload);
            }

            case CMD_SSL_HANDSHAKE -> {
                Log.d(TAG, "recv SSL/TLS handshake data, len = " + payloadLength);

                try {
                    tlsService.doHandshake(buffer, payloadOffset + COMMAND_ID_LENGTH, payloadLength - COMMAND_ID_LENGTH, out -> {
                        byte[] data = new byte[out.remaining()];
                        out.get(data);
                        mb.sendMessage(unencryptedParams, CMD_SSL_HANDSHAKE, data);
                    });
                } catch (SSLException e) {
                    Log.e(TAG, "exception during SSL handshake", e);
                    mb.closeConnection();
                }
            }

            case CMD_AUTH_COMPLETE -> {
                Log.i(TAG, "auth complete");

                if (tlsService.needsHandshake()) {
                    Log.wtf(TAG, "auth complete before handshake completed?");
                    mb.closeConnection();
                    return;
                }

                Log.i(TAG, "sending service discovery request");
                mb.sendMessage(encryptedParams, CMD_SERVICE_DISCOVERY_REQUEST, ServiceDiscoveryRequest.getDefault().serialize());
            }

            case CMD_SERVICE_DISCOVERY_RESPONSE -> {
                Log.d(TAG, "service discovery response");

                if (tlsService.needsHandshake()) {
                    Log.wtf(TAG, "service discovery response before handshake completed?"); // a request shouldn't have been sent yet
                    mb.closeConnection();
                    return;
                }

                if (videoChannel != null) {
                    Log.wtf(TAG, "service discovery response after video init?");
                    mb.closeConnection();
                    return;
                }

                ServiceDiscoveryResponse response = ServiceDiscoveryResponse.parse(buffer, payloadOffset + COMMAND_ID_LENGTH, payloadLength - COMMAND_ID_LENGTH);
                if (response == null) {
                    Log.e(TAG, "failed to parse service discovery response, bailing!");
                    mb.closeConnection();
                    return;
                }

                Log.d(TAG, "response data: " + response);
                handleServiceDiscoveryResponse(response);

                if (videoChannelMeta != null) {
                    Log.d(TAG, "init video channel");
                    videoChannel = new VideoChannel(mb, projectionService, videoChannelMeta);
                    videoChannel.openChannel();
                }

                if (mediaAudioChannelMeta != null) {
                    connectionServiceBinder.requestMediaProjection(mediaProjection -> {
                        if (!mb.isAlive()) return;

                        Log.d(TAG, "init media audio channel");
                        mediaAudioChannel = new AudioChannel(mb, mediaAudioChannelMeta, (preset, bufferSize) -> {
                            // TODO: handle permission and api requirement
                            return new AudioRecordCapture(
                                    new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                                            .excludeUsage(USAGE_ALARM)
                                            .build(),
                                    preset,
                                    bufferSize
                            );
                        });
                        mediaAudioChannel.openChannel();
                    });
                }

                if (inputChannelMeta != null) {
                    Log.d(TAG, "init input channel");
                    inputChannel = new InputChannel(mb, inputChannelMeta);
                    inputChannel.openChannel();
                    projectionService.setInput(inputChannel);
                }
            }

            default -> {
                Log.w(TAG, "control command not handled: " + command);
                Log.d(TAG, "payload: " + hexDump(buffer, payloadOffset, payloadLength));
            }
        }
    }
}
