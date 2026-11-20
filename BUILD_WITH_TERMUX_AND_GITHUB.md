# Build AndroidToolByAsiman v0.7.0 from Termux + GitHub Actions

## What v0.7.0 adds
- ADB-over-USB Host connection to a second Android phone.
- USB permission prompt on the Redmi host and RSA authorization prompt on the target phone.
- Read-only model, Android version, build, security patch, ABI, battery, storage, and boot-state properties from the target phone.
- Firmware lookup prefers the ADB-connected phone profile.

## Update an existing clone
1. Download `AndroidToolByAsiman_v0_7_0_ADB_Host.zip` to the phone's `Download` folder.
2. In Termux, allow storage access once:

```bash
termux-setup-storage
```

3. Extract the project over the existing clone:

```bash
cd ~
unzip -o ~/storage/downloads/AndroidToolByAsiman_v0_7_0_ADB_Host.zip -d ~
cd ~/AndroidToolByAsiman
```

4. Commit and push:

```bash
git status
git add -A
git commit -m "Add USB ADB connected-device diagnostics v0.7.0"
git push origin main
```

If Git says `nothing to commit`, stop and check that the ZIP was extracted to `~/` and that `~/AndroidToolByAsiman/app/src/main/java/com/asiman/androidtool/MainActivity.kt` contains `Подключить телефон через ADB`.

## Download the APK
1. Open the repository on GitHub.
2. Open **Actions** and select the newest `Build AndroidToolByAsiman APK` run.
3. Wait for every step to finish successfully.
4. Under **Artifacts**, download `AndroidToolByAsiman-v0.7.0-debug` and extract `app-debug.apk`.

The APK is not ready until GitHub Actions reports a successful build. This source archive has not been built on the target Redmi phone yet.

## Connect a second phone
1. On the second phone, enable Developer options and USB debugging.
2. Connect the second phone to the Redmi host with a data-capable OTG adapter/cable.
3. Open AndroidToolByAsiman → **USB**.
4. Grant Android's USB permission if prompted.
5. Press **Подключить телефон через ADB**.
6. Unlock the second phone and approve the RSA debugging fingerprint.
7. Read the separate **Подключённый телефон (ADB)** panel.

If no ADB interface appears, change the second phone's USB mode/cable, ensure USB debugging is on, then disconnect/reconnect. Some OEM builds do not expose USB ADB in every USB mode.

## Limits
- No Fastboot flashing or partition writing.
- No bootloader/FRP/screen-lock bypass.
- ADB properties are read-only. Missing OEM properties are shown as unavailable, not guessed.
- Unlocking the bootloader must use the manufacturer's official process and can erase data.
