# Car App Hub vehicle menu recovery

This directory contains the native menu source and a repeatable build for the
original Car App Hub 3.1.1 preview debug package. The original APK or its debug
ZIP is the input. This recovery build is separate from the legacy Gradle app.

The native grid uses the existing selected apps, with icons loaded from the
phone and a default icon when needed. The interface uses the Android light or
dark configuration. The content screen moves Menu, Back and Stop to a side rail
on wide screens and a bottom row on short screens. The original activity handles
launch callbacks, capture and driving restriction state. Its input mapping uses
the resized surface and rejects gestures that began before a size change or end
outside the visible projection area.

The **Display** button is available during projection. **Fit** shows the whole
image. **Fill** covers the available content area and may crop the edges. It
falls back to Fit when the requested buffer would exceed 4096 pixels on either
axis or 4 Mi pixels in area. Portrait and landscape selections are independent
for the lifetime of the car screen. The viewport follows the phone's reported
orientation. Video player chrome drawn by the phone stays part of the image.

The browser design is in `design/car-ui-preview/`. Recent apps and the appearance
dialog remain in the browser preview. Isolated app capture and video performance
require separate work.

## Build

Use Python 3, JDK 17, Android SDK platform 36, Build Tools 36.0.0 and Apktool 2.12.1.

```sh
python3 recovery-ui/build.py \
  --apktool /path/to/apktool_2.12.1.jar \
  --android-jar "$ANDROID_HOME/platforms/android-36/android.jar" \
  --build-tools "$ANDROID_HOME/build-tools/36.0.0" \
  Car-App-Hub-3.1.1-preview-debug.zip car-app-hub-ui-unsigned.apk
```

Set `--jdk-bin` if Java and Javac are outside `PATH`. The build verifies the
baseline APK hash. It compiles the native menu and checks that the recovered
activity changes only in presentation and its viewport/touch methods. It compares all untouched
original APK files, including the manifest, Android resources and Xposed
configuration, and checks the final ZIP alignment.

Run `ViewportSizeCheck` as a plain JVM main with `ViewportSize.java` to check
portrait and landscape scaling, the fill limit and touch clipping. The tests
cover geometry. DHU or vehicle testing is needed for surface composition.

A test package signed with a new key requires a device without the previous
installation. Android requires the original signer for an in-place update.
Use a separate test device to keep the working installation. DHU and physical
vehicle validation remain open. The Git branch stores source and build tooling.
Signing keys and APKs are managed separately.
