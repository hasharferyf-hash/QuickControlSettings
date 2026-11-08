# QuickControlSettings

A compact Hebrew Android dashboard for direct device controls.

## Permission strategy

The manifest declares the standard and special permissions that may be useful for the implemented controls and planned Wi-Fi, Bluetooth, location and NFC features. Runtime permissions should be requested only when the user invokes a feature that needs them; the app must not request an unrelated bundle of permissions at startup.

### ADB-grantable permission

With USB debugging enabled, run:

```sh
adb shell pm grant QuickControlSettings.brisker.AI android.permission.WRITE_SECURE_SETTINGS
```

This is a development-level permission and is grantable through ADB on supported builds. It does not make the app a privileged/system app.

### Permissions that need a different grant path

- `WRITE_SETTINGS`: special app access. It is not granted by `pm grant`.
- `ACCESS_NOTIFICATION_POLICY`: Notification Policy Access; it is not granted by `pm grant`.
- `CAMERA`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `NEARBY_WIFI_DEVICES`, and location permissions: runtime permissions, where applicable.
- `ACCESS_BACKGROUND_LOCATION`: subject to Android's separate background-location approval rules.

The app is intentionally designed not to open Android Settings screens. If a required special access has not already been provisioned, the related control remains unavailable and explains why.

## Device Owner compatibility

QuickControlSettings does **not** request or become Device Owner. It is intended to run alongside an already-enrolled Device Owner / DPC app.

Important: Device Owner privileges belong to the package that Android has designated as Device Owner. They are not inherited by other apps installed on the same device. If another DPC is Device Owner, QuickControlSettings cannot call that DPC's privileged DevicePolicyManager operations merely because Device Owner is active. To expose those capabilities inside this app, the DPC would need to provide an explicit, authenticated management interface, or QuickControlSettings would need to be granted the relevant privileged/system status by the device firmware.

## Android platform limitations

Declaring a permission is not the same as receiving it, and receiving a permission does not override API-level restrictions.

- Modern Android restricts ordinary apps from enabling/disabling Wi-Fi, Bluetooth, Location, Battery Saver, global dark theme and other protected system controls.
- Wi-Fi and Bluetooth permissions can allow discovery, connection or communication in supported cases; they do not generally authorize toggling the system radios.
- `WRITE_SECURE_SETTINGS` is not a universal system-control permission.
- `WRITE_SETTINGS` and Notification Policy Access require separate special access.
- Device Owner, privileged/system-app status, OEM APIs or root may be required for particular global operations, depending on Android version and firmware.

## Current direct controls

- Screen brightness and auto-rotate, when `WRITE_SETTINGS` access is already available.
- Media, ring, notification and alarm volume.
- Ringer mode.
- Flashlight, after CAMERA runtime permission is granted.
- Do Not Disturb, after Notification Policy Access is granted.
- App-local dark appearance only.

The current UI does not yet implement Wi-Fi, Bluetooth, Location, NFC or Battery Saver controls. Their manifest permissions are declared for future implementation where Android permits it; they do not imply those controls can be toggled on every device.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 35.

- Namespace / application ID: `QuickControlSettings.brisker.AI`
- Kotlin
- Gradle Kotlin DSL
- Minimum SDK: 26
- Target SDK: 35

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).
