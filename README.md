# Geargrinder
> [!caution]
> This is experimental software in active development. DO NOT USE WHILE DRIVING.

An Android Auto alternative for de-googled phones.

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

Only tested with emulators so far. Looking in to testing on OEM hardware soon (hopefully).

If you accept the risks and want to help by testing on your hardware, I would appreciate if you open an issue with the results (regardless of success).

Tested and working on these emulators:
- [OpenAuto](https://github.com/f1xpl/openauto/)
- [Headunit Reloaded](https://xdaforums.com/t/android-4-1-headunit-reloaded-for-android-auto-with-wifi.3432348/)

### phone compatibility

#### root

Launching the projection currently requires root for ease of development. This will change as the project matures.

You should be able to use audio capture without root.

#### video

Geargrinder uses the MediaCodec library for video and is subject to device-specific issues. Please report them if you encounter any.

Tested on:
- Qualcomm
- Mediatek
- Exynos

#### audio

> [!NOTE]
> DRM-protected audio is currently not captured.

Currently, your phone must natively support the audio sample rate for your headunit. The only easy way to check this is to just try it.


### working
- USB communication
- Video
- Audio
- Touch Input
- Basic UI


## thanks

These other projects have proved very helpful during development:

- [aasdk](https://github.com/f1xpl/aasdk/)
- [OpenAuto](https://github.com/f1xpl/OpenAuto/)

