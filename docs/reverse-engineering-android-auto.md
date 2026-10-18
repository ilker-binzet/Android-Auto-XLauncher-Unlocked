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

The activities used by `LaunchOptionsFragment` must still be declared in the manifest.
`aapt2` from the Android SDK build tools is the quickest check:

```sh
aapt2 dump xmltree android-auto.apk --file AndroidManifest.xml | grep -c '"com.google.android.projection.gearhead.companion.settings.Material3SettingsActivity"'
```

`rea search-android-classes android-auto.apk <Name>` confirms whether the class itself is gone.
The app hides launch options whose activity is missing in the installed Android Auto version.

## Verify the installer check

Most Android Auto classes are obfuscated. JADX places them in the `defpackage` package,
so pass `defpackage.<name>` to REA.

1. Find which dex methods call the installer APIs (`dexdump` from the Android SDK build tools):

   ```sh
   unzip -o android-auto.apk 'classes*.dex' -d dex
   for d in dex/*.dex; do dexdump -d "$d" > "$d.dump"; done
   grep -E 'InstallSourceInfo;\.get(Initiating|Installing)PackageName|getInstallerPackageName|getInstallSourceInfo' dex/*.dump
   ```

   Android 11+ calls go through `...$$ExternalSyntheticApiModelOutline0` helpers;
   grep the dumps for calls to those helpers to find the real callers.

2. Decompile the callers with REA:

   ```sh
   rea inspect-android-method android-auto.apk defpackage.jld v
   ```

   The validator logs `CarProjectionValidator#isPackageAllowed3p`, search for that string
   when the obfuscated names change.

3. JADX can mangle complex branches (look for `JADX WARN: Code duplicated`),
   confirm the decision in the `dexdump` bytecode of the same method.

If Android Auto switched to another API, add a hook for it in `AAXUnlock.hookSpecs()`.

The same tools are available to AI agents through REA's MCP server (`npx rea-agents setup`).

## Findings: Android Auto 18.0.664204-release

`minSdk` 32, `targetSdk` 37.

**Launch options:** 10 of the 11 activities are still declared.
`InteractiveTroubleshooterActivity` was removed (no manifest entry, no class left).

**Installer check** (`CarProjectionValidator#isPackageAllowed3p`, obfuscated as `jld.v`):

1. Allowed right away when an internal flag is set, or the app is Google signed.
2. The installer is `InstallSourceInfo#getInitiatingPackageName`. When that is
   `com.google.android.packageinstaller`, it uses `getInstallingPackageName` instead.
   Before Android 11 it uses `PackageManager#getInstallerPackageName`.
3. Allowed when the installer is in the `AppValidation__allowed_3p_installers` flag.
   The default value only lists Korean carrier stores (SKT, KT, LG U+), each pinned to its signing certificate.
4. Otherwise, allowed when the installer is `com.android.vending` **and** the Play Store is Google signed.
   This is decided before, and independently of, the remote Play Store check (`Finsky.IsValid`).
5. Otherwise, allowed only when the Play Store service reports `Finsky.IsValid`.
   The service is only asked when the Play Store is Google signed.

The module reports `com.android.vending` as installer, which satisfies step 4 on devices with the official Play Store.
On devices where the Play Store is not Google signed (e.g. some microG setups), steps 4 and 5 both fail,
so the installer hook alone does not unlock apps there.

Other installer API callers only report Android Auto's own install source for telemetry.
