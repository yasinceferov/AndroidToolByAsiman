# AndroidToolByAsiman v0.7.1

Android diagnostics utility with separate host-phone and ADB-connected-phone information.

## Device modes
- **Host phone**: Android properties of the phone running this app.
- **Connected phone**: USB Host discovery and ADB over USB. Enable USB debugging on the target phone, connect it with a data cable/OTG adapter, grant the USB permission in AndroidToolByAsiman, press **Connect phone via ADB**, then approve the RSA debugging key on the target phone.
- Once ADB is connected, the app reads the target phone's manufacturer, model, device/product codename, Android version/SDK, build ID/fingerprint, security patch, ABI list, battery percentage, and `/data` storage estimate. It also reads boot-state properties where exposed by the OEM.

## Other features
- USB VID/PID, manufacturer/product strings, interface and endpoint diagnostics.
- Heuristic ADB/Fastboot interface identification.
- Firmware lookup is enabled only after the target phone is ADB-authorized. It uses the connected target profile (manufacturer, model, device codename, product, build ID, and build fingerprint) and never silently falls back to the host phone. Search opens browser results; it does not automatically download or flash firmware.
- Firmware file selection and SHA-256 verification.
- Read-only security/boot-state information and links to Xiaomi's official unlock instructions.

## Limitations
- This release does not flash partitions and does not implement Fastboot write/flash operations.
- Fastboot is identified from USB descriptors only; it is not a complete Fastboot client.
- Bootloader bypass, FRP bypass, screen-lock bypass, exploit-based unlocking, and unauthorized access are not supported.
- ADB requires USB debugging, a data-capable cable, USB Host support, USB permission, and RSA authorization. Some OEMs may restrict properties or commands.
- Bootloader properties can be missing or vendor-dependent; an unavailable value is not proof of lock/unlock state.

## Build requirements
- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- Gradle 8.13 / Android Gradle Plugin 8.13.2
- Kotlin 2.2.21

The app consumes the Apache-2.0 `Ernest-su/adb` library (`com.github.Ernest-su:adb:v0.4.0`) through JitPack for ADB USB transport and RSA authentication.

## GitHub Actions
Push to `main`; open **Actions** and download artifact `AndroidToolByAsiman-v0.7.1-debug` after a successful build.


## Версия 0.8.0
Добавлены расширенные ADB-сведения о батарее, экране, режиме загрузки, SELinux и времени работы, а также экспорт диагностического отчёта.
