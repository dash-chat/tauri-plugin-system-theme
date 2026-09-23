package org.dashchat.systemtheme

import android.app.Activity
import android.content.res.Configuration
import android.webkit.WebView
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
    private var webView: WebView? = null

    override fun load(webView: WebView) {
        this.webView = webView
        activity.runOnUiThread {
            SystemTheme.makeNavigationBarTransparent(activity.window)
        }
    }

    @Command
    fun setColorSchemePreference(invoke: Invoke) {
        val args = invoke.parseArgs(SchemeArgs::class.java)

        activity.runOnUiThread {
            SystemTheme.setColorSchemePreference(activity, activity.window, args.scheme)
        }

        invoke.resolve()
    }

    @Command
    fun overrideSystemBarsColorScheme(invoke: Invoke) {
        val args = invoke.parseArgs(OptionalSchemeArgs::class.java)

        activity.runOnUiThread {
            // A host that keeps the webview clear of the bars shows its themed
            // window background behind them, not the overlay the override is for.
            val scheme = if (webViewUnderStatusBar()) args.scheme else null
            SystemTheme.overrideSystemBarsColorScheme(activity, activity.window, scheme)
        }

        invoke.resolve()
    }

    private fun webViewUnderStatusBar(): Boolean {
        val webView = webView ?: return true
        val location = IntArray(2)
        webView.getLocationInWindow(location)
        return location[1] == 0
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        activity.runOnUiThread {
            SystemTheme.onConfigurationChanged(activity.window, newConfig)
        }
    }
}
