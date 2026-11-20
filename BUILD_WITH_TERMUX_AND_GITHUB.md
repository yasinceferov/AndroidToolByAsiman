# AndroidToolByAsiman — build APK with Termux + GitHub

## Recommended path: GitHub Actions (build happens on GitHub)
You do not need a full Android SDK installed in Termux.

1. Install Termux from its official F-Droid/GitHub distribution.
2. Install Git:
   pkg update && pkg upgrade
   pkg install git
3. Create a GitHub repository named AndroidToolByAsiman.
4. Upload/push the project files so the repository root contains:
   settings.gradle.kts
   build.gradle.kts
   gradle.properties
   app/
   .github/workflows/build-apk.yml
5. In Termux, clone your repository (replace YOUR_NAME):
   git clone https://github.com/YOUR_NAME/AndroidToolByAsiman.git
   cd AndroidToolByAsiman
6. If you make edits locally, commit and push:
   git add .
   git commit -m "Update AndroidToolByAsiman"
   git push
7. On GitHub open Actions -> Build AndroidToolByAsiman APK -> Run workflow.
   A push to main also starts a build automatically.
8. Open the completed workflow run -> Artifacts -> AndroidToolByAsiman-debug.
   Download and extract the APK on your phone.

## Optional: build locally in Termux
A local Android Gradle build on a phone is more fragile and may fail due to
RAM, storage, native build tools, or Android SDK setup. GitHub Actions is the
recommended reproducible build path. Do not run random curl|bash install scripts.

## Current implementation status
This repository is a starter prototype. It currently includes device-information
display, USB device discovery, firmware web search, file selection and SHA-256
calculation. It does NOT yet implement a complete ADB host, Fastboot host,
bootloader unlock, or actual partition flashing. The GitHub workflow builds the
current project; it does not magically add those capabilities.

Before flashing any device, verify exact model/codename, region, partition layout,
image signature/hash, battery level, and a recovery path. Flashing the wrong
image can permanently brick a device. Unlocking normally wipes user data.

## v0.5.0 USB test
1. Install the debug APK on the host phone.
2. Open USB tab before connecting the target, then connect using a data-capable OTG adapter/cable.
3. Tap **Обновить USB** and verify that a device appears.
4. Tap **Запросить разрешение Android** and approve the system dialog.
5. Verify VID/PID and interface details appear. Repeat with the target in normal Android, Recovery and Fastboot modes if supported.
6. Record host model, target model, USB mode, VID/PID, interface class/subclass/protocol and whether permission was granted.

This test only validates USB enumeration and permission. It does not validate ADB authentication, Fastboot commands or flashing.
