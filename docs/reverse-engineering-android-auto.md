# Checking a new Android Auto version with REA

Android Auto renames and removes its internal activities between releases.
When a launch option stops working, or the installer check moves,
[REA](https://github.com/morluto/rea) can be used to inspect the Android Auto APK
without its source code.

## Setup

REA needs Node.js 22.19+ and, for APKs, a full JDK 17+ and the headless JADX MCP jar.
See the [REA Android guide](https://github.com/morluto/rea/blob/main/docs/android-analysis.md).

```sh
npm install --global rea-agents
curl -LO https://github.com/1013503897/jadx-headless-mcp/releases/download/v0.7.1/jadx-headless-mcp-0.7.1-all.jar
# Must match the hash REA pins for this JAR
echo "6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75  jadx-headless-mcp-0.7.1-all.jar" | sha256sum -c
export REA_JADX_MCP_JAR="$PWD/jadx-headless-mcp-0.7.1-all.jar"
rea doctor
```

Tested with REA 6.7.0.

Pull the installed Android Auto APK from a device:

```sh
adb shell pm path com.google.android.projection.gearhead
adb pull /data/app/.../base.apk android-auto.apk
```

## Verify the launch options

The activities used by `LaunchOptionsFragment` must still be declared in the manifest:

```sh
rea inspect-android-package android-auto.apk
rea search-android-classes android-auto.apk Material3SettingsActivity
rea search-android-classes android-auto.apk DeveloperSettingsActivity
rea search-android-classes android-auto.apk VnLaunchPadInternalActivity
```

## Verify the installer check

The Xposed module (`xposed/AAXUnlock.kt`) reports Google Play as the installer of every app.
Check which installer APIs Android Auto still calls:

```sh
rea trace-android-references android-auto.apk android.content.pm.InstallSourceInfo --method-name getInitiatingPackageName
rea trace-android-references android-auto.apk android.content.pm.InstallSourceInfo --method-name getInstallingPackageName
rea trace-android-references android-auto.apk android.content.pm.PackageManager --method-name getInstallerPackageName
```

These are framework classes, so they are not part of the APK itself.
If a trace comes back empty, search for the Android Auto classes that handle app allow-listing
and inspect their methods with `rea inspect-android-method` instead.

If Android Auto switched to another API, add a hook for it in `AAXUnlock.hookSpecs()`.

The same tools are available to AI agents through REA's MCP server (`npx rea-agents setup`).
