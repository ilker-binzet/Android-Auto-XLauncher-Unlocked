#!/usr/bin/env python3
"""Build the native car menu revision against the recovered 3.1.1 APK."""

import argparse
import copy
import hashlib
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path


ORIGINAL_HASH = "2a23d82f71490ecdb41ab9d6671a2bebe1fd354b04e7207eb2c88532b057c134"
ORIGINAL_NAME = "Car-App-Hub-3.1.1-preview-debug.apk"
PACKAGE = "com/google/android/projection/gearhead/xunlocked"
GRID = f"L{PACKAGE}/recovery/CarMenuGrid;"
VIEWPORT = f"L{PACKAGE}/recovery/ProjectionViewport;"
ACTIVITY = f"L{PACKAGE}/projection/CarMirrorActivity;"
CAPTURE = f"L{PACKAGE}/projection/ProjectionCaptureService;"
STRINGS = f"L{PACKAGE}/R$string;"
FOLDER = Path(__file__).resolve().parent


def command(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def source_file(root, suffix):
    return root / "smali_classes7" / PACKAGE / "projection" / suffix


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f"Expected a unique anchor in the recovered activity: {old!r}")
    return text.replace(old, new, 1)


def patch_activity(activity):
    original = activity.read_text()
    # Preserve the original visibility and item click calls while changing the
    # apps field to the native GridView subclass.
    updated = original.replace("Landroid/widget/ListView;", GRID)
    updated = replace_once(updated,
        f"    invoke-virtual {{v10, v9}}, {GRID}->setDividerHeight(I)V\n", "")
    updated = replace_once(updated,
        f"    new-instance v3, L{PACKAGE}/projection/CarMirrorActivity$showApps$2;\n\n"
        f"    invoke-direct {{v3, v1, v4}}, L{PACKAGE}/projection/CarMirrorActivity$showApps$2;"
        f"-><init>({ACTIVITY}Ljava/util/List;)V\n\n"
        "    check-cast v3, Landroid/widget/ListAdapter;",
        f"    iget-object v3, v1, {ACTIVITY}->packages:Ljava/util/List;\n\n"
        f"    invoke-static {{v1, v3, v4}}, {GRID}"
        "->adapter(Landroid/content/Context;Ljava/util/List;Ljava/util/List;)Landroid/widget/ListAdapter;\n\n"
        "    move-result-object v3")
    updated = replace_once(updated,
        "    move-object v3, v0\n\n    check-cast v3, Landroid/view/View;\n\n"
        f"    invoke-virtual {{p0, v3}}, {ACTIVITY}->setContentView(Landroid/view/View;)V",
        f"    sget v3, {STRINGS}->projection_ready:I\n\n"
        f"    invoke-static {{v0, v3}}, {GRID}->wrap(Landroid/widget/LinearLayout;I)Landroid/view/View;\n\n"
        "    move-result-object v3\n\n"
        f"    invoke-virtual {{p0, v3}}, {ACTIVITY}->setContentView(Landroid/view/View;)V")
    updated = patch_viewport(updated)
    # Catch changes outside presentation and its viewport/touch boundary.
    method = re.compile(r"^\.method[^\n]+\n.*?^\.end method$", re.M | re.S)
    old_methods = {m.splitlines()[0]: m for m in method.findall(original)}
    new_methods = {m.splitlines()[0]: m for m in method.findall(updated)}
    if old_methods.keys() != new_methods.keys():
        raise ValueError("Activity method signatures changed")
    viewport_methods = {"showApps()V", "onCreate(Landroid/os/Bundle;)V", "resizeSurface()V",
                        "surfaceChanged(Landroid/view/SurfaceHolder;III)V"}
    for name, body in old_methods.items():
        if name.split()[-1] not in viewport_methods and " surfaceCreated$lambda$22(" not in name:
            if body != new_methods[name].replace(GRID, "Landroid/widget/ListView;"):
                raise ValueError(f"Unexpected change in {name}")
    activity.write_text(updated)


def patch_viewport(activity):
    # The existing service supplies the actual phone dimensions. Keep that service,
    # the current SurfaceHolder callbacks and the resume/vehicle checks in place.
    resize = re.compile(r"^\.method private final resizeSurface\(\)V\n.*?^\.end method$", re.M | re.S)
    match = resize.search(activity)
    if match is None:
        raise ValueError("Could not locate the original resizeSurface method")
    updated = replace_once(activity, match.group(), f""".method private final resizeSurface()V
    .locals 2

    sget-object v0, {CAPTURE}->Companion:L{PACKAGE}/projection/ProjectionCaptureService$Companion;
    invoke-virtual {{v0}}, L{PACKAGE}/projection/ProjectionCaptureService$Companion;->getActive(){CAPTURE}
    move-result-object v0
    if-eqz v0, :done

    invoke-virtual {{v0}}, {CAPTURE}->phoneSize()Landroid/graphics/Point;
    move-result-object v0
    iget-object v1, p0, {ACTIVITY}->surface:Landroid/view/SurfaceView;
    if-eqz v1, :done
    invoke-static {{v1, v0}}, {VIEWPORT}->resize(Landroid/view/SurfaceView;Landroid/graphics/Point;)V

    :done
    return-void
.end method""")
    updated = replace_once(updated,
        f"    .line 207\n    invoke-direct {{p0}}, {ACTIVITY}->connectSurface()V",
        f"    .line 207\n    invoke-direct {{p0}}, {ACTIVITY}->connectSurface()V\n\n"
        f"    iget-object v0, p0, {ACTIVITY}->surface:Landroid/view/SurfaceView;\n"
        f"    invoke-static {{v0, p3, p4}}, {VIEWPORT}->surfaceSized(Landroid/view/SurfaceView;II)V")
    return replace_once(updated,
        "    .line 191\n    :cond_0\n    invoke-virtual {p2}, Landroid/view/MotionEvent;->getActionMasked()I",
        f"""    .line 191
    :cond_0
    iget-object v2, p0, {ACTIVITY}->surface:Landroid/view/SurfaceView;
    invoke-static {{v2, p2}}, {VIEWPORT}->accepts(Landroid/view/View;Landroid/view/MotionEvent;)Z
    move-result p1
    if-nez p1, :viewport_accepts
    iput-boolean v0, p0, {ACTIVITY}->touching:Z
    return v0

    :viewport_accepts
    invoke-virtual {{p2}}, Landroid/view/MotionEvent;->getActionMasked()I""")


def original_apk(input_path):
    if input_path.suffix.lower() == ".apk":
        data = input_path.read_bytes()
    else:
        with zipfile.ZipFile(input_path) as archive:
            if archive.getinfo(ORIGINAL_NAME).file_size != 27_408_701:
                raise ValueError("The uploaded APK has an unexpected size")
            data = archive.read(ORIGINAL_NAME)
    if hashlib.sha256(data).hexdigest() != ORIGINAL_HASH:
        raise ValueError("Use the original Car App Hub 3.1.1 preview debug APK for this build")
    return data


def rewrite_apk(original, rebuilt, overlay, output):
    with zipfile.ZipFile(original) as before, zipfile.ZipFile(rebuilt) as changed:
        names = set(before.namelist())
        if "classes10.dex" in names or "classes7.dex" not in names:
            raise ValueError("Unexpected original DEX layout")
        with zipfile.ZipFile(output, "w") as after:
            for info in before.infolist():
                if re.fullmatch(r"META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))", info.filename, re.I):
                    continue
                data = changed.read(info.filename) if info.filename == "classes7.dex" else before.read(info)
                after.writestr(copy.copy(info), data)
            after.write(overlay, "classes10.dex", compress_type=zipfile.ZIP_STORED)
        with zipfile.ZipFile(output) as after:
            if after.testzip() is not None:
                raise ValueError("The rebuilt APK has a damaged archive entry")
            for name in (set(after.namelist()) & names) - {"classes7.dex"}:
                if after.read(name) != before.read(name):
                    raise ValueError(f"Untargeted APK entry changed: {name}")
            if b"CarMenuGrid;" not in after.read("classes10.dex") or b"CarMenuGrid;" not in after.read("classes7.dex"):
                raise ValueError("The native menu or its activity binding is missing")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="original 3.1.1 debug APK or debug ZIP")
    parser.add_argument("output", type=Path, help="new unsigned APK output path")
    parser.add_argument("--apktool", type=Path, required=True, help="Apktool 2.12.1 jar")
    parser.add_argument("--android-jar", type=Path, required=True, help="installed Android SDK platform android.jar")
    parser.add_argument("--build-tools", type=Path, required=True, help="directory with Android SDK d8 and zipalign")
    parser.add_argument("--jdk-bin", type=Path, help="JDK bin directory if java and javac are not on PATH")
    args = parser.parse_args()
    output = args.output.resolve()
    if output.exists():
        parser.error(f"output already exists: {output}")
    java = str(args.jdk_bin / "java") if args.jdk_bin else "java"
    javac = str(args.jdk_bin / "javac") if args.jdk_bin else "javac"
    apktool = [java, "-jar", args.apktool.resolve()]
    output.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="carhub-ui-") as directory:
        work = Path(directory)
        original = work / ORIGINAL_NAME
        original.write_bytes(original_apk(args.input))
        decoded, compiled = work / "decoded", work / "compiled"
        compiled.mkdir()
        command(javac, "--release", "8", "-classpath", args.android_jar, "-d", compiled,
                *sorted((FOLDER / "src").rglob("*.java")))
        classes = work / "ui.jar"
        with zipfile.ZipFile(classes, "w", zipfile.ZIP_DEFLATED) as jar:
            for path in sorted(compiled.rglob("*.class")):
                jar.write(path, path.relative_to(compiled).as_posix())
        dex_out = work / "dex"
        dex_out.mkdir()
        command(java, "-cp", args.build_tools / "lib/d8.jar", "com.android.tools.r8.D8",
                "--min-api", "28", "--lib", args.android_jar, "--output", dex_out, classes)
        command(*apktool, "decode", "--no-res", "-o", decoded, original)
        patch_activity(source_file(decoded, "CarMirrorActivity.smali"))
        rebuilt = work / "rebuilt.apk"
        command(*apktool, "build", decoded, "-o", rebuilt)
        repackaged = work / "repackaged.apk"
        rewrite_apk(original, rebuilt, dex_out / "classes.dex", repackaged)
        aligned = work / "aligned.apk"
        command(args.build_tools / "zipalign", "-p", "-f", "4", repackaged, aligned)
        command(args.build_tools / "zipalign", "-c", "-p", "4", aligned)
        output.write_bytes(aligned.read_bytes())
    print(f"Built unsigned candidate: {output}")


if __name__ == "__main__":
    main()
