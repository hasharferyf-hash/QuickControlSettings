# QuickControlSettings

A Hebrew Android quick-settings dashboard inspired by Samsung's quick panel.
It uses native Material controls, RTL layout, rounded blue active tiles and light/dark surfaces.

## Everyday controls

- Wi-Fi, Bluetooth, location, Do Not Disturb and Battery Saver: open the corresponding Android settings or supported inline panel. These tiles say that they open settings; they never pretend to toggle a protected radio.
- Flashlight: toggles after camera permission is approved, and follows the camera service's actual state.
- Sound mode: cycles through ring, vibrate and silent, with an access shortcut if Android blocks a change.
- Auto-rotate: toggles with Modify system settings access.
- Brightness and media volume: always within reach, with readable values and accessible labels. Dragging brightness switches to manual brightness.
- Expandable sound/display controls: ring, notification and alarm volume, screen timeout, touch vibration and automatic brightness.
- Shortcuts: internet, airplane mode, screen casting, storage, alarms, accessibility, display/text size and all settings. NFC appears only when the device has NFC.
- App appearance: saved light/dark preference on Android 8 and above.

Settings destinations vary between manufacturers. When a specific page is unavailable the app tries a related screen, then general Settings. Opening a shortcut requires no location or Bluetooth scanning permissions.

## Permissions

No permission bundle is requested at startup. Camera access is requested when the flashlight is first used. Modify system settings and Notification Policy Access open Android's own access screens when needed. Unused location, nearby-device, background, boot and service permissions have been removed.

Advanced tools are collapsed by default. They offer device dark appearance, Battery Saver, animation scales and stay-awake while charging. These remain dependent on Android/OEM support. An ADB permission is optional:

```sh
adb shell pm grant QuickControlSettings.brisker.AI android.permission.WRITE_SECURE_SETTINGS
```

The in-app copy button copies only this command. It does not grant unrelated permissions. WRITE_SETTINGS and Notification Policy Access have separate approval paths; WRITE_SECURE_SETTINGS is not root and does not bypass all platform restrictions.

The app does not become Device Owner and cannot inherit another management app's privileges.

## Build and checks

Use JDK 17, Android SDK 35 and Gradle 8.9 (Android Studio can supply the SDK).

```sh
gradle :app:assembleDebug :app:lintDebug
# With an Android emulator/device attached:
gradle :app:connectedDebugAndroidTest
```

- Application ID: `QuickControlSettings.brisker.AI`
- Minimum SDK: 26; target/compile SDK: 35
- Kotlin, native Android Views, Material 3

Pull-request CI compiles the app and test APK, runs Android Lint, then exercises the real activity on an Android 15 emulator. UI checks cover startup without special permissions, RTL, accessible tiles, expanded-section restoration, app appearance and 150% text. Light/dark/large-text screenshots and reports are uploaded as workflow artifacts. Release signing and tag publishing follow the existing workflow.

Before shipping, also check a physical Samsung phone: camera permission denial, flashlight availability while using the camera, hardware volume buttons, rotation changes, three-button/gesture navigation, all shortcuts, DND behavior and advanced controls after an ADB grant. OEM-specific behavior cannot be established by an emulator alone.

## Suggested next improvements

1. Let people choose their favorite tiles and reorder them.
2. Add a home-screen widget and native Quick Settings tiles for the most-used controls.
3. Move all Hebrew copy into string resources before adding more languages.
4. Split device-control logic from the Activity and add capability-specific tests.
5. Add screenshots and a short first-run guide to the release page.

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).
