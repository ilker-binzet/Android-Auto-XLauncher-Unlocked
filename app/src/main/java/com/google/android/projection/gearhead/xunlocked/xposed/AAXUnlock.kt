package com.google.android.projection.gearhead.xunlocked.xposed

import android.annotation.SuppressLint
import android.content.pm.InstallSourceInfo
import android.os.Build
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method

/**
 * libxposed (API 102) entry point.
 *
 * Makes the "installed from Google Play" checks inside Android Auto always pass,
 * so sideloaded 3rd party Android Auto apps are accepted.
 * Hook credits: Fermata by @AndreyPavlenko.
 *
 * Verified against Android Auto 18.0.664204 (see docs/reverse-engineering-android-auto.md):
 * `CarProjectionValidator#isPackageAllowed3p` reads [InstallSourceInfo.getInitiatingPackageName]
 * (falling back to `getInstallingPackageName` for the system package installer) and allows the app
 * when that installer is `com.android.vending` and the Play Store is Google signed,
 * without waiting for the remote Play Store verification.
 */
class AAXUnlock : XposedModule() {

    /** A single hook, identified by a stable id so it can be replaced on hot reload */
    private class HookSpec(val id: String, val target: Method, val hooker: XposedInterface.Hooker)

    @Volatile
    private var hooked = false

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != AA_PKG || hooked) return
        hooked = true
        log(Log.INFO, TAG, "Hooking package $AA_PKG")

        for (spec in hookSpecs()) {
            runCatching { hook(spec.target).setId(spec.id).intercept(spec.hooker) }
                .onFailure { log(Log.ERROR, TAG, "Failed to hook ${spec.id}", it) }
        }
    }

    /** The hooks are stateless, so nothing has to be released before the old code is retired */
    override fun onHotReloading(param: HotReloadingParam): Boolean = true

    /** Atomically swap the hooks installed by the previous module generation for the new ones */
    override fun onHotReloaded(param: HotReloadedParam) {
        val oldHandles = param.oldHookHandles
        if (oldHandles.isEmpty()) return
        hooked = true

        val specs = hookSpecs().associateBy { it.id }
        for (handle in oldHandles) {
            val spec = specs[handle.id]
            if (spec == null) handle.unhook() else handle.replaceHook(spec.hooker)
        }
        log(Log.INFO, TAG, "Hot reloaded ${oldHandles.size} hook(s)")
    }

    @SuppressLint("PrivateApi")
    private fun hookSpecs(): List<HookSpec> {
        val specs = mutableListOf<HookSpec>()

        // Android 11+ installer checks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (name in listOf("getInitiatingPackageName", "getInstallingPackageName")) {
                specs += HookSpec(
                    "InstallSourceInfo#$name",
                    InstallSourceInfo::class.java.getDeclaredMethod(name),
                    PLAY_STORE_HOOKER)
            }
        }

        // Legacy installer check, still used by older code paths
        runCatching {
            Class.forName("android.app.ApplicationPackageManager")
                .getDeclaredMethod("getInstallerPackageName", String::class.java)
        }.onSuccess {
            specs += HookSpec("ApplicationPackageManager#getInstallerPackageName", it, PLAY_STORE_HOOKER)
        }.onFailure { log(Log.WARN, TAG, "getInstallerPackageName not found", it) }

        return specs
    }

    private companion object {
        const val TAG = "AAXUnlock"
        const val AA_PKG = "com.google.android.projection.gearhead"
        const val GPLAY_PKG = "com.android.vending"

        /** Report Google Play as installer without calling the original method */
        val PLAY_STORE_HOOKER = XposedInterface.Hooker { GPLAY_PKG }
    }
}
