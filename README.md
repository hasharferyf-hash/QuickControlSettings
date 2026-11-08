# QuickControlSettings

A compact Hebrew Android dashboard for everyday device controls.

## Direct controls currently implemented

- Screen brightness, when Android's special Modify System Settings access has already been granted.
- Media, ring, notification and alarm volume.
- Ringer mode: normal, vibrate and silent.
- Flashlight, after granting the camera permission.
- Auto-rotate, when Modify System Settings access has already been granted.
- Do Not Disturb, when Notification Policy Access has already been granted.
- App-local dark appearance (does not change the whole device theme).

The app intentionally does not open Android Settings screens. If Android does not permit a control for an ordinary third-party app, the dashboard explains that limitation instead of navigating away or claiming success.

## Android platform limitations

Adding permissions to the manifest does not grant privileged access. In particular:

- `WRITE_SECURE_SETTINGS` is a signature/development permission and can be granted with ADB on supported builds. It is not a universal system-control permission.
- `WRITE_SETTINGS` requires the user to grant special access outside the app. It is distinct from `WRITE_SECURE_SETTINGS`.
- Do Not Disturb requires Notification Policy Access, also granted by the user outside the app.
- Modern Android restricts ordinary apps from toggling several global controls (including Battery Saver, Wi-Fi, global dark theme, location and some Bluetooth/NFC operations). Device Owner, privileged/system-app status, or root may be required depending on the operation and Android version.

The app does not attempt to bypass these platform security boundaries.

## ADB setup

For the development permission, connect the device with USB debugging enabled and run:

```sh
adb shell pm grant QuickControlSettings.brisker.AI android.permission.WRITE_SECURE_SETTINGS
```

This does not automatically grant `WRITE_SETTINGS`, Notification Policy Access, or privileged platform permissions.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 35.

- Namespace / application ID: `QuickControlSettings.brisker.AI`
- Kotlin
- Gradle Kotlin DSL
- Minimum SDK: 26
- Target SDK: 35

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).
