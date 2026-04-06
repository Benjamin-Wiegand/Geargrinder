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

### authentication

Some (most?) certified/OEM Android Auto headunits verify an SSL certificate provided by the phone to check if it originated from the Android Auto app, and may refuse to connect if this verification fails.

There are some exceptions, but I don't have a large enough sample to verify if they are the exception or the rule yet.

To use Geargrinder with these headunits, you need to obtain the private key and SSL certificate used by the official Android Auto app. Instructions for this are not yet available in this repository, this process is not trivial, **and there is no secret menu in the Android Auto app which exposes these (to my knowledge)**, but there are multiple ways to go about it if you know what you're doing, and other ways if not (see tip below). 

> [!tip]
> You may also be able to find working (but usually expired) Android Auto app key/certificate pairs from elsewhere on the Internet. These will work, but usually only if you to set the clock back on your headunit to before they expired.

Once you obtain matching private key and certificate files, you can import them into Geargrinder via the settings menu (Settings > Certificates and keys). If successful, you should now be allowed to enable the "Use imported keys" option. Once enabled, the imported key and certificate will be used the next time you try connecting to your headunit.

### headunit compatibility

> [!warning]
> I have no idea how your car will react to communications from this app. Be prepared for unforeseen consequences.
> 
> If you accept the risks and want to help, please open an issue with the results (regardless of success).

> [!note]
> Some headunits **may** work with Geargrinder's self-signed keys. If not, then you may need to use keys and certificates extracted from the Android Auto app.
> Please see the [authentication](#authentication) section for more information.

Tested and working on OEM car headunits from these cars:
- 2021-2024 Nissan Kicks SV/SR
    - Model number: PN-4300
    - Software Version: 432
    - Authentication: Works with both self-signed (CN=Bob) and imported keys. Date is adjustable.
    - Notes: Only tested outside of a car, I don't have the car, just the stereo.


### emulator compatibility

Tested and working on these emulators:
- [OpenAuto](https://github.com/f1xpl/openauto/)
- [Headunit Reloaded](https://xdaforums.com/t/android-4-1-headunit-reloaded-for-android-auto-with-wifi.3432348/)

### phone compatibility

#### root/Shizuku
> [!NOTE]
> - KernelSU has not been tested yet.

Root is no longer required to run Geargrinder on your phone.

Geargrinder currently requires you to either provide root access (via [Magisk](https://github.com/topjohnwu/Magisk/), [KernelSU](https://github.com/tiann/KernelSU/), etc.) or ADB access via [Shizuku](https://github.com/RikkaApps/Shizuku/).

A "no root" mode is planned which will only support screen mirroring.

#### operating system
> [!NOTE]
> - Your device may not work with Geargrinder even if the OS is supported due to differences between vendors.
> - Geargrinder does not currently work with touch input on Android 9 and below.

Geargrinder currently targets Android 8 and above.

Geargrinder has been verified to work on these operating systems (this list may not be current):
- LineageOS (de-Googled, some with microG)
    - LineageOS 18 (AOSP 11)
    - LineageOS 21 (AOSP 14)
    - LineageOS 22 (AOSP 15)
    - LineageOS 23 (AOSP 16)
- GrapheneOS (no GMS)
    - latest (Android 16)
- Stock Android (Google Pixel, with Google services)
    - Android 9
    - Android 10
    - Android 16
- Stock Android (various vendors, de-googled)
    - Android 14
    - Android 15
- MIUI (de-googled)
    - MIUI 11 (Android 8)

Any OS not listed above has yet to be tested.

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
    - TLS encryption
    - Service discovery (mostly)
- Video
    - H.264 at 480p, 720p, and 1080p, 30 and 60 fps (requires your phone to support the given mode)
- Audio
    - raw PCM audio (requires device support for sample rate and bit depth)
- User input
    - touch screen input (with multitouch)
    - phone keyboard input
    - basic button input
    - basic rotary input
- Basic UI
    - open apps on the display
    - split screen
    - dock with app shortcuts, time, battery, and network indicators
    - app drawer
    - pop-up notifications with TTS


## thanks

Non-code contributions:

- [Nick](https://github.com/4channel) for helping test on his car
- [Zelemar](https://zelemar.eu) for helping me diagnose an OEM headunit for bench testing

Other projects of great help during development:

- [aasdk](https://github.com/f1xpl/aasdk/)
- [OpenAuto](https://github.com/f1xpl/OpenAuto/)

