package org.dashchat.systemtheme

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.Window
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat

private const val PREFS_NAME = "system-theme"
private const val COLOR_SCHEME_KEY = "color-scheme"
private const val FLIP_DELAY_MS = 250L

/**
 * The app's colour scheme, applied to the Android configuration so the resource
 * system resolves `-night` resources for it — including the window background
 * the system draws before our process starts.
 */
object SystemTheme {
    private var barsColorSchemeOverridden = false

    /** Apply [scheme] ("light", "dark", or anything else to follow the system) and store it. */
    fun setColorSchemePreference(context: Context, window: Window, scheme: String) {
        // Only [applyPersisted] reads this back, and only below API 31 — above
        // that the system persists the scheme for us.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(COLOR_SCHEME_KEY, scheme)
                .apply()
        }
        applyNightMode(context, scheme)
        barsColorSchemeOverridden = false
        applyBarsAppearance(window, isDark(context, scheme))
    }

    /**
     * Force the bar icon colour, for overlays whose background ignores the app
     * theme. A null [scheme] gives up the override and tracks the theme again.
     */
    fun overrideSystemBarsColorScheme(context: Context, window: Window, scheme: String?) {
        barsColorSchemeOverridden = scheme != null
        applyBarsAppearance(
            window,
            scheme?.equals("light") ?: isNight(context.resources.configuration),
        )
    }

    /**
     * Keep the navigation bar see-through, so the app's own surface reaches the
     * screen edge. Set on the window rather than declared in the theme because
     * the system's contrast scrim gets turned back on once the activity is up
     * (androidx's `enableEdgeToEdge` does exactly that).
     */
    @Suppress("DEPRECATION")
    fun makeNavigationBarTransparent(window: Window) {
        // Ignored from API 35 on, where the bar is always transparent anyway.
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    /**
     * A recreated activity comes with a fresh window, which needs the bars set
     * up again, and a reloaded page, which no longer holds a bars override.
     */
    fun applyToRecreatedWindow(context: Context, window: Window) {
        makeNavigationBarTransparent(window)
        barsColorSchemeOverridden = false
        applyBarsAppearance(window, isNight(context.resources.configuration))
    }

    /**
     * Re-apply the stored scheme at activity creation. On API 31+ the system
     * persists it and has already drawn the starting window with it before the
     * process started, so there is nothing left to do.
     */
    fun applyPersisted(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        val scheme = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(COLOR_SCHEME_KEY, null) ?: return
        applyNightMode(context, scheme)
    }

    /**
     * Applying a scheme reaches the configuration asynchronously, and the system
     * theme can change under a "system" scheme, so the bars are re-derived here
     * rather than only at the point the scheme is set.
     */
    fun onConfigurationChanged(window: Window, newConfig: Configuration) {
        if (barsColorSchemeOverridden) return
        applyBarsAppearance(window, isNight(newConfig))
    }

    private fun isDark(context: Context, scheme: String) = when (scheme) {
        "light" -> false
        "dark" -> true
        else -> isNight(context.resources.configuration)
    }

    private fun isNight(configuration: Configuration) =
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun applyNightMode(context: Context, scheme: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            // Only YES/NO become a per-app override; every other mode maps to
            // UI_MODE_NIGHT_UNDEFINED, clearing it so the app follows the system again.
            uiModeManager.setApplicationNightMode(
                when (scheme) {
                    "light" -> UiModeManager.MODE_NIGHT_NO
                    "dark" -> UiModeManager.MODE_NIGHT_YES
                    else -> UiModeManager.MODE_NIGHT_AUTO
                }
            )
        } else {
            AppCompatDelegate.setDefaultNightMode(
                when (scheme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }

    /**
     * Only the icon colour: the bars themselves stay transparent.
     *
     * Some skins repaint the icons when the appearance changes rather than when
     * it is set, and take the first value after the window is created without
     * ever drawing it. Writing the flipped value first is a change they cannot
     * miss — well clear of a frame, since the appearance only reaches the window
     * manager once per traversal and two writes in one would cancel out. The
     * flip is what the bars already show whenever it matters, so it goes unseen.
     */
    private fun applyBarsAppearance(window: Window, lightIcons: Boolean) {
        setBarsAppearance(window, !lightIcons)
        window.decorView.postDelayed({ setBarsAppearance(window, lightIcons) }, FLIP_DELAY_MS)
    }

    private fun setBarsAppearance(window: Window, lightIcons: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !lightIcons
        controller.isAppearanceLightNavigationBars = !lightIcons
    }
}
