package com.google.android.projection.gearhead.xunlocked

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Helpers to run commands as SuperUser, off the main thread */
object RootShell {

    private const val TAG = "AAXLU-RootShell"

    /** Package name of the Android Auto app */
    const val AA_PACKAGE = "com.google.android.projection.gearhead"

    /**
     * Run a command as SuperUser
     *
     * @param cmd Command to run
     * @return `true` if `su` was granted and the command exited successfully
     */
    suspend fun runCommand(cmd: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val su = ProcessBuilder("su").redirectErrorStream(true).start()
            su.outputStream.bufferedWriter().use {
                it.write(cmd)
                it.newLine()
                it.write("exit")
                it.newLine()
            }
            val output = su.inputStream.bufferedReader().use { it.readText() }
            val exitCode = su.waitFor()
            if (output.isNotBlank()) Log.i(TAG, output.trim())
            // `am start` reports missing activities on its output while exiting with 0
            exitCode == 0 && !output.contains("Error", ignoreCase = true)
        } catch (e: IOException) {
            Log.e(TAG, "Could not run su", e)
            false
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while running su", e)
            false
        }
    }

    /**
     * Launch a package activity as SuperUser
     *
     * @param packageName Name of the package
     * @param activityName Name of the activity
     */
    suspend fun launchActivity(packageName: String, activityName: String): Boolean =
        runCommand("am start -n '$packageName/$activityName'")

    /**
     * Launch an Android Auto activity as SuperUser
     *
     * @param activityName Name of the activity
     */
    suspend fun launchAndroidAutoActivity(activityName: String): Boolean =
        launchActivity(AA_PACKAGE, activityName)
}
