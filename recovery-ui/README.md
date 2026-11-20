# Car App Hub vehicle menu recovery

This directory contains the native menu source and a repeatable build for the
original Car App Hub 3.1.1 preview debug package. The original APK or its debug
ZIP is the input. This recovery build is separate from the legacy Gradle app.

The native grid uses the existing selected apps, with icons loaded from the
phone and a default icon when needed. The interface uses the Android light or
dark configuration. The content screen moves Menu, Back and Stop to a side rail
on wide screens and a bottom row on short screens. The original activity handles
launch callbacks, capture, input coordinates and driving restriction state.

The browser design is in `design/car-ui-preview/`. The recovery build includes
the app grid and content controls. Recent apps, appearance and Fit/Fill settings
remain designs in the browser preview. Isolated app capture and video performance
remain separate work requiring device verification.

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
activity changes only at the two presentation entry points, plus the grid
type used for existing visibility calls. The build compares all untouched
original APK files, including the manifest, Android resources and Xposed
configuration, and checks the final ZIP alignment.

A test package signed with a new key requires a device without the previous
installation. Android requires the original signer for an in-place update.
Use a separate test device to keep the working installation. DHU and physical
vehicle validation remain open. The Git branch stores source and build tooling.
Signing keys and APKs are managed separately.
