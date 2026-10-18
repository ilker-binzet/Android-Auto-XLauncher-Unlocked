package com.google.android.projection.gearhead.xunlocked

//region Imports

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.projection.gearhead.xunlocked.RootShell.AA_PACKAGE
import com.google.android.projection.gearhead.xunlocked.databinding.FragmentLaunchOptionsBinding
import kotlinx.coroutines.launch

//endregion Imports

/** The main [Fragment] subclass of Android Auto - XLauncher Unlocked */
class LaunchOptionsFragment : Fragment() {

    //region Initialization

    // Initialize fragment bindings
    private var _binding: FragmentLaunchOptionsBinding? = null
    private val binding get() = _binding!!

    // Initialize settings
    private var defaultActivityName: String = ""
    private var useDefault: Boolean = false
    private lateinit var settings: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        _binding = FragmentLaunchOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Constructor for the main view fragment */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Keep the content clear of the navigation bar when drawing edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }

        // Load the stored settings if existing
        settings = requireActivity().getSharedPreferences("pref", Context.MODE_PRIVATE)
        useDefault = settings.getBoolean("use_default", false)
        defaultActivityName = settings.getString("default_activity_name", "").orEmpty()

        // Setup the default checkbox
        binding.checkboxDefault.isChecked = useDefault
        binding.checkboxDefault.setOnClickListener { toggleUseDefault() }

        // Setup all the launcher options
        binding.buttonClassic.setOnClickListener { launchClassicSettings() }
        binding.buttonMaterial3.setOnClickListener { launchSuAndroidAutoActivity(MATERIAL3_SETTINGS) }
        for ((button, activityName) in mapOf(
            binding.buttonInteractiveTroubleshooter to INTERACTIVE_TROUBLESHOOTER,
            binding.buttonTroubleshooter to TROUBLESHOOTER,
            binding.buttonDeveloperSettings to DEVELOPER_SETTINGS,
            binding.buttonEngineerSettings to ENGINEER_SETTINGS,
            binding.buttonAppQualityTester to APP_QUALITY_TESTER,
            binding.buttonFeedbackReports to FEEDBACK_REPORTS,
            binding.buttonPrimesEvents to PRIMES_EVENTS,
            binding.buttonAssistantShortcut to ASSISTANT_SHORTCUT,
            binding.buttonAppLauncher to APP_LAUNCHER,
        )) {
            button.setOnClickListener { launchSuAndroidAutoActivity(activityName) }
        }

        // Setup the Xposed module status card
        binding.buttonRequestScope.setOnClickListener { requestAndroidAutoScope() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                XposedStatus.refresh()
                XposedStatus.info.collect { updateModuleStatus(it) }
            }
        }

        // Launch the default launcher option if used, but not again on configuration changes
        if (savedInstanceState == null && useDefault && defaultActivityName.isNotEmpty()) {
            launchDefault()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    //endregion Initialization

    //region Module status

    /** Show whether the libxposed module is active and hooked into Android Auto */
    private fun updateModuleStatus(info: XposedStatus.Info?) {
        val binding = _binding ?: return
        binding.textviewModuleStatus.text = when {
            info == null -> getString(R.string.module_status_inactive)
            info.androidAutoInScope -> getString(
                R.string.module_status_active,
                info.frameworkName, info.frameworkVersion, info.apiVersion)
            else -> getString(R.string.module_status_no_scope, info.frameworkName, info.frameworkVersion)
        }
        binding.buttonRequestScope.isVisible = info != null && !info.androidAutoInScope
    }

    /** Ask the Xposed framework to add Android Auto to the module scope */
    private fun requestAndroidAutoScope() {
        XposedStatus.requestAndroidAutoScope { approved, message ->
            // Scope callbacks arrive on a binder thread
            activity?.runOnUiThread {
                if (!approved && isAdded) {
                    toast(getString(R.string.module_scope_failed, message.orEmpty()))
                }
            }
        }
    }

    //endregion Module status

    // region Launcher Options

    /** Launch the configured default activity */
    private fun launchDefault() {
        if (defaultActivityName.equals(CLASSIC, ignoreCase = true)) launchClassicSettings()
        else launchSuAndroidAutoActivity(defaultActivityName)
    }

    /** Launch the Classic Android Auto settings, does not require root */
    private fun launchClassicSettings() {
        updateDefaultActivityName(CLASSIC)
        Log.i(LOG_TAG, "Attempting to launch AA Classic settings...")

        val intent = Intent()
            .setComponent(ComponentName(AA_PACKAGE, CLASSIC_SETTINGS))
            .putExtra("com.google.android.gearhead.projection.phonescreen.SHORTCUT", true)

        try {
            startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            Log.e(LOG_TAG, "Could not launch AA Classic settings, activity not found", ex)
            toast(getString(R.string.launch_failed))
        } catch (ex: SecurityException) {
            Log.e(LOG_TAG, "Could not launch AA Classic settings, permission denied", ex)
            toast(getString(R.string.launch_failed))
        }
    }

    /** Launch an Android Auto activity as SuperUser + update the default if used */
    private fun launchSuAndroidAutoActivity(activityName: String) {
        updateDefaultActivityName(activityName)
        Log.i(LOG_TAG, "Attempting to launch $activityName...")

        viewLifecycleOwner.lifecycleScope.launch {
            if (!RootShell.launchAndroidAutoActivity(activityName)) {
                Log.e(LOG_TAG, "Could not launch $activityName")
                toast(getString(R.string.launch_failed_root))
            }
        }
    }

    // endregion Launcher Options

    //region Helpers

    /** Toggle the use default launch option setting */
    private fun toggleUseDefault() {
        useDefault = !useDefault
        settings.edit { putBoolean("use_default", useDefault) }
        binding.checkboxDefault.isChecked = useDefault

        // Clear the default activity name if not using a default launch option
        updateDefaultActivityName()
    }

    /** Update the default activity name, clear if not using a default */
    private fun updateDefaultActivityName(activityName: String = "") {
        defaultActivityName = if (useDefault) activityName else ""
        settings.edit { putString("default_activity_name", defaultActivityName) }
    }

    private fun toast(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }

    //endregion Helpers

    private companion object {
        const val LOG_TAG = "AndroidAuto-LauncherUnlocked"
        const val CLASSIC = "classic"

        // Known Android Auto activities
        const val CLASSIC_SETTINGS =
            "com.google.android.gearhead.vanagon.VnLaunchPadInternalActivity"
        const val MATERIAL3_SETTINGS =
            "$AA_PACKAGE.companion.settings.Material3SettingsActivity"
        const val INTERACTIVE_TROUBLESHOOTER = "com.google.android.apps.auto" +
                ".components.settings.interactivetroubleshooter.InteractiveTroubleshooterActivity"
        const val TROUBLESHOOTER =
            "com.google.android.apps.auto.components.settings.troubleshooter.TroubleshooterActivity"
        const val DEVELOPER_SETTINGS = "$AA_PACKAGE.companion.devsettings.DeveloperSettingsActivity"
        const val ENGINEER_SETTINGS = "$AA_PACKAGE.companion.settings.EngineerSettingsActivity"
        const val APP_QUALITY_TESTER = "$AA_PACKAGE.companion.devsettings.AppQualityTesterActivity"
        const val FEEDBACK_REPORTS = "$AA_PACKAGE.companion.feedback.FeedbackListActivity"
        const val PRIMES_EVENTS =
            "com.google.android.libraries.performance.primes.debug.PrimesEventActivity"
        const val ASSISTANT_SHORTCUT = "$AA_PACKAGE.companion.settings.AddAssistantShortcutActivity"
        const val APP_LAUNCHER = "$AA_PACKAGE.companion.settings.LauncherAppSettingsActivity"
    }
}
