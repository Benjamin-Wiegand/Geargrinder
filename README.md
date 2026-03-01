# Geargrinder
> [!caution]
> This is experimental software in active development. DO NOT USE WHILE DRIVING.

An Android Auto alternative for de-googled phones. Full protocol-level re-implementation from scratch.

![picture of it running](https://ben.wiegand.pw/img/geargrinder-dev-notice-thumb.webp)

## warnings

This is experimental software:

- Do not use it while driving.
- I am not responsible for bricked headunits.
- There are no published builds yet.

## status

Still in development.

### headunit compatibility

> [!warning]
> I have no idea how your car will react to communications from this app. Be prepared for unforeseen consequences.

Only working emulators and potentially some knockoff/uncertified headunits so far. Certified OEM headunits won't work until authentication is properly implemented and will likely require cryptographic keys to be provided.

If you accept the risks and want to help by testing on your hardware, I would appreciate if you open an issue with the results (regardless of success).

Tested and working on these emulators:
- [OpenAuto](https://github.com/f1xpl/openauto/)
- [Headunit Reloaded](https://xdaforums.com/t/android-4-1-headunit-reloaded-for-android-auto-with-wifi.3432348/)

### phone compatibility

#### root

Launching the projection currently requires root for ease of development. This will change as the project matures.

You should be able to use audio capture/streaming without root.

#### video

Geargrinder uses the MediaCodec library for video encoding. Please report any device-specific issues.

Tested and working on a select few devices from these platforms:
- Qualcomm
- Mediatek
- Exynos

#### audio

> [!NOTE]
> DRM-protected audio is currently not captured.

Currently, your phone must natively support the audio sample rate for your headunit. The only easy way to check this is to just try it.


### working
- USB communication
- Protocol communication
    - Framing and multi-frame messages
    - TLS encryption (auth is TODO)
    - Service discovery (mostly)
- Video
    - H.264 at 480p, 720p, and 1080p, 30 and 60 fps (requires your phone to support the given mode)
- Audio
    - raw PCM audio (requires device support for sample rate and bit depth)
- User input
    - touch screen input (with multitouch)
    - phone keyboard input
    - basic button input
- Basic UI
    - open apps on the display
    - split screen
    - bar with quick-launch app icons and time


## thanks

Non-code contributions:

- [Nick](https://github.com/4channel) for helping test on his car

Other projects of great help during development:

- [aasdk](https://github.com/f1xpl/aasdk/)
- [OpenAuto](https://github.com/f1xpl/OpenAuto/)

