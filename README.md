# tauri-plugin-system-theme

A Tauri plugin that applies your app's colour scheme at the system level, so every
native surface follows it — including the window the system paints *before* the
webview exists.

That last part is the reason this plugin exists. A webview-side theme switch can
only take effect once the page has rendered, which leaves a flash of the wrong
background on every cold launch for anyone whose in-app choice differs from their
system theme.

## Platform Support

| Platform | Colour scheme | Bar icons |
|----------|---------------|-----------|
| Android  | `UiModeManager.setApplicationNightMode` (API 31+), `AppCompatDelegate` below | Supported |
| iOS      | `UIWindow.overrideUserInterfaceStyle` | Status bar supported; no navigation bar, and the home indicator picks its own contrast |
| Desktop  | `AppHandle::set_theme` | n/a |

On Android the scheme is persisted *by the system*, which is what lets it theme
the starting window before the process runs. Below API 31 that is not possible;
the scheme is re-applied at activity creation instead, so only the starting
window itself is unthemed.

Two known limits:

- **Linux** treats `set_theme(None)` as "light" rather than "drop the override"
  (tao's `WindowRequest::SetTheme`), so `system` falls back to the theme the app
  launched with and cannot track later changes to the system theme.
- **iOS** resolves its launch storyboard before the app runs, and unlike Android
  there is no per-app persisted override that reaches it.

## Installation

### Rust

```toml
[dependencies]
tauri-plugin-system-theme = { git = "https://github.com/dash-chat/tauri-plugin-system-theme", branch = "main" }
```

```rust
builder = builder.plugin(tauri_plugin_system_theme::init());
```

The stored scheme is applied on its own once the webview is ready — there is
nothing to call at startup, and no Kotlin or Swift wiring to add. Below API 31,
Android re-applies it from a content provider this library declares in its own
manifest, which the system instantiates before the first activity.

### JavaScript

```
npm install tauri-plugin-system-theme
```

`signalium` is a **peer** dependency: the reactive exports below must share the
consumer's instance, or reads will not track.

### Capabilities

Add the permission to a capabilities file that covers every platform:

```json
{ "permissions": ["system-theme:default"] }
```

## Host contract

The plugin controls the *native* surfaces. Four things it cannot do for you —
miss any one and you still get a flash:

**1. Make the webview transparent**, so the native window shows through until the
page paints. On Android, in `tauri.android.conf.json`:

```json
{ "app": { "windows": [{ "transparent": true }] } }
```

**2. Declare the colour scheme before your stylesheets**, or the webview paints a
white canvas before any CSS has parsed:

```html
<meta name="color-scheme" content="light dark" />
```

**3. Use a `DayNight` app theme** whose `windowBackground` is defined in both
`values/` and `values-night/` — that resource is what the system draws for the
starting window:

```xml
<style name="Theme.YourApp" parent="Theme.MaterialComponents.DayNight.NoActionBar">
    <item name="android:windowBackground">@color/app_background</item>
</style>
```

**4. List `uiMode` in your activity's `configChanges`**, otherwise Android
recreates the activity on a theme change (reloading the webview) and the bar
icons stop tracking:

```xml
<activity android:configChanges="...|uiMode" />
```

The plugin owns the nav bar's *icon colour* only. Its colour and contrast stay
your theme's business.

On Android WebViews older than M136, which report the system bars as 0 in
`env(safe-area-inset-*)`, the plugin pads the webview in from the bars instead of
leaving it edge-to-edge, and `overrideSystemBarsColorScheme` becomes a no-op since
the bars then sit over the themed window background.

## Usage

```typescript
import {
  colorScheme,
  colorSchemePreference,
  setColorSchemePreference,
  overrideSystemBarsColorScheme,
} from 'tauri-plugin-system-theme';

// The theme in effect: 'light' | 'dark', synchronous, correct on the first
// frame. Render from this.
const dark = colorScheme() === 'dark';

// The stored preference: 'light' | 'dark' | 'system'. A ReactivePromise, since
// it has to be fetched. For a UI that offers the choice.
const preference = await colorSchemePreference();

// Persisted, and applied to every native surface.
await setColorSchemePreference('dark');

// Transient: force the bar icons for an overlay whose background ignores the
// app theme (a lightbox, say). Never persisted.
await overrideSystemBarsColorScheme('light');
await overrideSystemBarsColorScheme(null); // back to tracking the app theme
```

`'system'` clears the override rather than pinning a value, so the app follows
the device again — which is why `colorScheme()` can simply read the webview's
`prefers-color-scheme`: the native layer has already resolved the preference into
it. Tauri's own `theme()`/`onThemeChanged()` cannot serve that role, being async
and unsupported on mobile.

Changing the scheme emits `system-theme://changed`, so several windows stay in
step.

## License

AGPL-3.0
