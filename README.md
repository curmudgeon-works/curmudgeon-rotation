# Curmudgeon Rotation

Per-app screen rotation control for Android, plus a Quick Settings tile for instant
Portrait / Landscape / Auto. No ads, no tracking, no internet permission.

Built for two annoyances:

- **Apps that only rotate when system auto-rotate is on** (YouTube). A rule can switch
  auto-rotate on while the app is open and put your setting back when you leave.
- **Apps that lock their own orientation** (HBO Max stays portrait even with auto-rotate on).
  A "force" rule adds an invisible window that asks Android for landscape, overriding the app.

## Features

- **Quick Settings tile**: tap cycles Auto → Portrait → Landscape (optionally → Reverse
  landscape); icon and subtitle show the current state; long-press opens the app. On
  Android 13+ the app can add the tile with one tap. Works without any per-app detection.
- **Per-app rules**, optionally limited to specific screens (activities), e.g. only an app's
  video player. Actions:
  Follow system (rule paused) · Auto-rotate on · Auto-rotate off (keep current rotation) ·
  Portrait · Landscape · Reverse landscape · Force landscape (overlay) · Force portrait
  (overlay) · Force auto / full sensor (overlay).
- **Restores your rotation** when a rule stops applying. If you change rotation yourself while
  a rule is active, your change wins and is not overwritten.
- **Two orientation mechanisms**:
  - *System setting*: writes `Settings.System.ACCELEROMETER_ROTATION` and `USER_ROTATION`.
  - *Overlay*: a 0×0, non-touchable, non-focusable window with `screenOrientation` set, either
    as `TYPE_ACCESSIBILITY_OVERLAY` (from the accessibility service) or `TYPE_APPLICATION_OVERLAY`.
- **Two foreground-app detection methods**: an opt-in accessibility service (recommended), or
  usage-access polling from a foreground service (only while the screen is on).
- Transient windows (keyboard, system UI, notification shade, share sheet, optionally the
  launcher, plus your own ignore list) never switch rules; debounce against flicker.
- Export / import rules as JSON through the system file picker.
- Simple / Advanced settings.

## Permissions and why

| Permission | Required? | Why |
|---|---|---|
| Modify system settings (`WRITE_SETTINGS`) | For the tile and non-overlay rules | Turn auto-rotate on/off and set the locked rotation. Only those two settings are written. |
| Accessibility service | Optional (recommended for rules) | Know which app/screen is in front (package + class name only), and host the overlay window for "force" rules. Cannot read window content. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Optional | App-overlay variant of "force" rules, and forcing from the tile without the accessibility service. |
| Usage access (`PACKAGE_USAGE_STATS`) | Optional | Alternative foreground-app detection if you don't want the accessibility service. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) | Automatic | Keeps usage-access polling, the quick-actions notification, or an app-overlay window alive. Runs only while one of those is enabled. |
| Notifications (`POST_NOTIFICATIONS`) | Optional | The quick-actions notification (Portrait / Landscape / Auto buttons). Asked for only when you enable it. |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Optional | "Start on boot" for usage-access detection / the notification. Does nothing unless enabled. |

Every permission has an explanation screen in the app before you are sent to the system
setting. The accessibility disclosure must be accepted before accessibility settings open.

There is **no** `INTERNET` permission.

## Privacy

- The app cannot connect to the internet. Nothing leaves the device.
- The accessibility service reads only the package name and class name of the window that comes
  to the front. It does not read window content, typed text, passwords or notifications.
- Rules, settings and a short log of recently seen screens (used to suggest screens in the rule
  editor; viewable and clearable in settings) are stored in the app's private storage.
- Cloud backup is disabled (`allowBackup="false"`).
- Exported rule files go only where you choose to save them.

## Limitations

- **Overlays can be hidden**: on Android 12+ an app can hide `TYPE_APPLICATION_OVERLAY` windows
  while it is in front, so app-overlay forcing may not work in every app. The accessibility
  overlay is not affected by that, which is why it is the default overlay type.
- On large screens (smallest width ≥ 600 dp) Android 16+ may ignore orientation requests from
  apps; forcing may have no effect there.
- Sideloaded APKs on Android 13+: the accessibility switch is greyed out ("Restricted setting")
  until you open App info → ⋮ → *Allow restricted settings*.
- Usage-access detection reacts up to one poll interval late.

## Building

See [BUILDING.md](BUILDING.md).

## License

Copyright © 2026 the Curmudgeon Rotation authors. Licensed under the GNU General Public License v3.0 only
(`GPL-3.0-only`); see [LICENSE](LICENSE).
