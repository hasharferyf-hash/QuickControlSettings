# QuickControlSettings

Android utility app for frequently used device controls and settings shortcuts.

## Features

- Screen brightness (requires the special **Modify system settings** access).
- Media, alarm, and ring volume.
- Flashlight.
- Auto-rotate.
- Ringer mode: normal, vibrate, silent.
- Do Not Disturb (requires Notification Policy Access).
- Shortcuts to Battery Saver, Dark theme, Wi-Fi, Bluetooth, Location, NFC, hotspot and display settings.
- In-app setup guide for granting `WRITE_SECURE_SETTINGS` with ADB.

## Important Android limitations

Android does not allow an ordinary third-party app to control every system setting. `WRITE_SECURE_SETTINGS` is a signature/development permission, not a universal administrator permission. It can be granted by ADB on supported builds, but it does **not** replace the separate special access for modifying system settings, Notification Policy Access, or privileged platform permissions. Some controls therefore open the relevant Android settings screen rather than silently changing protected state.

## Build

Open this repository in Android Studio (JDK 17, Android SDK 35). The project uses Kotlin and Gradle Kotlin DSL.

Package / application ID: `QuickControlSettings.brisker.AI`

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).
