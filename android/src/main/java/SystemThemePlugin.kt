package org.dashchat.systemtheme

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.Plugin

@InvokeArg
class SchemeArgs {
    lateinit var scheme: String
}

/** The bars override is given up by passing no scheme at all. */
@InvokeArg
class OptionalSchemeArgs {
    var scheme: String? = null
}

@TauriPlugin
class SystemThemePlugin(private val activity: Activity) : Plugin(activity) {
    private val keepsWebViewClearOfBars = !webViewReportsSystemBarInsets()
    private var currentActivity: Activity = activity

    override fun load(webView: WebView) {
        activity.runOnUiThread {
            SystemTheme.makeNavigationBarTransparent(activity.window)
            if (keepsWebViewClearOfBars) {
                insetContentFromSystemBars(activity)
            }
            followRecreatedActivities()
        }
    }

    // Tauri loads a plugin once per process, but the activity is recreated on
    // the config changes it doesn't handle itself (density, font scale, overlays).
    private fun followRecreatedActivities() {
        activity.application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(created: Activity, savedInstanceState: Bundle?) {
                if (created.javaClass == activity.javaClass) {
                    currentActivity = created
                    created.window.decorView.post { setUpRecreatedActivity(created) }
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun setUpRecreatedActivity(activity: Activity) {
        SystemTheme.applyToRecreatedWindow(activity, activity.window)
        if (keepsWebViewClearOfBars) {
            insetContentFromSystemBars(activity)
        }
    }

    // WebView only exposes the system bars through env(safe-area-inset-*) from
    // M136; older ones report 0 there, so the page would draw under the bars.
    private fun webViewReportsSystemBarInsets(): Boolean {
        val major = WebView.getCurrentWebViewPackage()?.versionName
            ?.substringBefore('.')?.toIntOrNull() ?: return false
        return major >= 136
    }

    /**
     * Keeps the webview between the system bars instead of edge-to-edge. The
     * webview gets the bars zeroed out, so a WebView that does report them
     * can't pad the page a second time.
     */
    private fun insetContentFromSystemBars(activity: Activity) {
        val content = activity.findViewById<View>(android.R.id.content)
        val bars = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val inset = insets.getInsets(bars)
            view.setPadding(inset.left, inset.top, inset.right, inset.bottom)
            WindowInsetsCompat.Builder(insets)
                .setInsets(bars, Insets.NONE)
                .setDisplayCutout(null)
                .build()
        }
        // The window dispatched its first insets before the plugin loaded.
        ViewCompat.requestApplyInsets(content)
    }

    @Command
    fun setColorSchemePreference(invoke: Invoke) {
        val args = invoke.parseArgs(SchemeArgs::class.java)

        currentActivity.runOnUiThread {
            SystemTheme.setColorSchemePreference(currentActivity, currentActivity.window, args.scheme)
        }

        invoke.resolve()
    }

    @Command
    fun overrideSystemBarsColorScheme(invoke: Invoke) {
        val args = invoke.parseArgs(OptionalSchemeArgs::class.java)

        currentActivity.runOnUiThread {
            // With the webview kept clear of the bars, they show the themed
            // window background, not the overlay the override is for.
            val scheme = if (keepsWebViewClearOfBars) null else args.scheme
            SystemTheme.overrideSystemBarsColorScheme(currentActivity, currentActivity.window, scheme)
        }

        invoke.resolve()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        currentActivity.runOnUiThread {
            SystemTheme.onConfigurationChanged(currentActivity.window, newConfig)
        }
    }
}
