#!/usr/bin/env python3
"""Recover a local edit and rebuild workspace from the archived 3.1.1 debug APK."""

import argparse
import hashlib
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path


APK_NAME = "Car-App-Hub-3.1.1-preview-debug.apk"
APK_SHA256 = "2a23d82f71490ecdb41ab9d6671a2bebe1fd354b04e7207eb2c88532b057c134"
PACKAGE = Path("com/google/android/projection/gearhead/xunlocked")
PROJECTION_ACTIVITY = PACKAGE / "projection/CarMirrorActivity.smali"
NOTICES = ("ACKNOWLEDGEMENTS.md", "LICENSE", "SDK-NOTICE.md", "SpaceGrotesk-OFL.txt")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("debug_zip", type=Path)
    parser.add_argument("output", type=Path, help="new directory for the decoded APK")
    parser.add_argument("--apktool-jar", required=True, type=Path, help="Apktool 2.12.1 jar")
    parser.add_argument("--verify-build", action="store_true", help="rebuild an unsigned APK locally")
    args = parser.parse_args()
    output = args.output.resolve()
    if output.exists():
        parser.error(f"output directory already exists: {output}")
    if not args.apktool_jar.is_file():
        parser.error(f"Apktool jar not found: {args.apktool_jar}")

    with zipfile.ZipFile(args.debug_zip) as archive:
        apk_info = archive.getinfo(APK_NAME)
        if apk_info.file_size != 27_408_701:
            parser.error("unexpected APK size for the original Car App Hub 3.1.1 debug build")
        apk_bytes = archive.read(apk_info)
        if hashlib.sha256(apk_bytes).hexdigest() != APK_SHA256:
            parser.error("APK hash does not match the original Car App Hub 3.1.1 debug build")
        notices = {name: archive.read(name) for name in NOTICES}

    apktool = ["java", "-jar", str(args.apktool_jar.resolve())]
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="carhub-recover-", dir=output.parent) as temporary:
        stage = Path(temporary)
        apk_path = stage / APK_NAME
        apk_path.write_bytes(apk_bytes)
        decoded = stage / "decoded"
        subprocess.run([*apktool, "decode", "-o", str(decoded), str(apk_path)], check=True)
        if not any((folder / PROJECTION_ACTIVITY).is_file() for folder in decoded.glob("smali*")):
            raise RuntimeError("The decoded APK is missing the vehicle activity")
        notice_dir = decoded / "RECOVERY-NOTICES"
        notice_dir.mkdir()
        for name, data in notices.items():
            (notice_dir / name).write_bytes(data)

        if args.verify_build:
            rebuilt = stage / "car-app-hub-3.1.1-unsigned.apk"
            subprocess.run([*apktool, "build", str(decoded), "-o", str(rebuilt)], check=True)
            with zipfile.ZipFile(apk_path) as original, zipfile.ZipFile(rebuilt) as candidate:
                if candidate.testzip() is not None:
                    raise RuntimeError("The rebuilt package contains a damaged member")
                required = {"AndroidManifest.xml", "resources.arsc"}
                required.update(name for name in original.namelist() if name.endswith(".dex"))
                required.update(name for name in original.namelist() if name.startswith("META-INF/xposed/"))
                missing = required.difference(candidate.namelist())
                if missing:
                    raise RuntimeError(f"The rebuilt package is missing: {sorted(missing)}")
                for name in required:
                    if name.startswith("META-INF/xposed/") and candidate.read(name) != original.read(name):
                        raise RuntimeError(f"The rebuild changed {name}")
            shutil.rmtree(decoded / "build", ignore_errors=True)
            shutil.move(rebuilt, decoded / rebuilt.name)
        decoded.rename(output)

    print(f"Recovered {output}")


if __name__ == "__main__":
    main()
