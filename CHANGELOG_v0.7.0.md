# AndroidToolByAsiman v0.7.0 — ADB USB Host integration

## Added
- A separate connected-device panel on the USB screen.
- USB attach/detach refresh handling.
- ADB USB interface discovery using the Android USB Host API.
- USB permission flow followed by ADB RSA authorization on the target phone.
- Read-only remote device properties through ADB: manufacturer, model, product/device codename, Android release/SDK, build fingerprint, security patch, ABI list, battery level and `/data` free/total storage.
- Read-only boot-state properties (`ro.boot.flash.locked`, `ro.boot.vbmeta.device_state`, `ro.boot.verifiedbootstate`) with a warning that missing values are inconclusive.
- Firmware search uses the ADB-connected device profile when one is connected; otherwise it uses the host phone.
- Xiaomi official unlock information links.
- Build workflow updated for Android SDK 36 and Gradle 8.13.

## Safety and limits
- No bootloader bypass, FRP bypass, screen-lock bypass, exploit-based unlock or unauthorized access.
- No Fastboot flash/write implementation. Fastboot USB descriptors are only identified heuristically.
- No firmware partition writes. Flash Manager only selects files and computes SHA-256.
- ADB USB requires a USB OTG host, a data-capable cable, USB debugging enabled on the target, Android USB permission on the host, and RSA approval on the target.
- Some OEMs disable or restrict ADB USB, `dumpsys`, `df`, or boot properties. Unsupported properties display as unavailable; that does not prove device state.

## Build
- Push to `main` and download the `AndroidToolByAsiman-v0.7.0-debug` artifact from GitHub Actions after a successful run.
