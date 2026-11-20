# AndroidToolByAsiman

Стартовый проект Android Studio на Kotlin + Jetpack Compose.

## Что уже есть
- Определение производителя, модели, codename/device, Android, SDK, build fingerprint/display и ABI.
- Оценка свободного/общего пространства и процента батареи (если API сообщает значение).
- Просмотр USB-устройств, обнаруженных Android USB Host API.
- Экран-подготовка для поиска прошивок и проверки безопасности.

## Сборка APK
1. Установите Android Studio (JDK 17).
2. Откройте эту папку как проект.
3. Дождитесь синхронизации Gradle.
4. Выберите Build -> Build Bundle(s) / APK(s) -> Build APK(s).
5. APK появится в `app/build/outputs/apk/debug/`.

## Важные ограничения
- APK не может универсально обходить bootloader, FRP, экранную блокировку или защиту производителя.
- Android не раскрывает всем приложениям состояние загрузчика/root и полный набор системных данных.
- USB Host API показывает обнаруженные USB-устройства, но само по себе не реализует ADB/Fastboot.
- Для ADB/Fastboot между телефонами потребуется отдельная реализация протокола, совместимый USB OTG-адаптер, разрешения пользователя и тестирование на конкретных моделях.
- Поиск и установка прошивок должны сверять точную модель, codename, регион, загрузчик, подпись и контрольную сумму.
- Проект не содержит эксплойтов, обхода блокировок или автоматической прошивки.

## Следующие этапы
1. Добавить экран уровня патчей безопасности и проверку известных CVE по версии/патчу.
2. Реализовать поиск официальных страниц прошивок по производителю и codename.
3. Добавить экспорт отчёта диагностики.
4. Разработать отдельный USB-компонент ADB/Fastboot с безопасным allowlist-команд и подтверждением действий.
5. Добавить backup-предупреждения и проверку целостности образов перед прошивкой.


## Добавлено в обновлении
- Экран Flash Manager.
- Выбор локального файла через системный picker.
- Расчёт SHA-256 и копирование хеша.
- Поиск прошивки в браузере по модели и codename.
- Отображение уровня патча безопасности Android.
- Расширенная навигация приложения.

## Что пока НЕ реализовано
Реальная запись разделов через Fastboot/fastbootd и полноценный ADB Host ещё не входят в эту сборку. USB Host обнаруживает подключённые USB-устройства, но это не означает, что телефон уже умеет выполнять fastboot flash. Для реального прошивания потребуется интегрировать и проверить USB Fastboot Host-стек, обработку vendor-specific команд, выбор разделов и проверку совместимости. Нельзя обещать поддержку всех Android-устройств одним универсальным способом.

Приложение не выполняет обходы блокировок или эксплуатацию уязвимостей. Оно рассчитано на устройства, которыми владеет пользователь или на обслуживание которых у него есть разрешение.


## Build APK using Termux + GitHub
See `BUILD_WITH_TERMUX_AND_GITHUB.md`. The repository includes a GitHub Actions
workflow at `.github/workflows/build-apk.yml` that builds a debug APK and
publishes it as a downloadable workflow artifact.

## Device compatibility note
The app can gather standard Android device properties on many devices. USB OTG,
ADB and Fastboot host operation depends on the controller, Android version,
USB permissions, target boot mode and OEM implementation. A universal
bootloader unlock or universal flashing support cannot be guaranteed.


## Redmi Note 14 4G test profile
- Adds best-effort detection for the Redmi Note 14 4G family.
- Recognizes the known base codename `tanzanite`, but prefers live `Build.DEVICE` / `Build.PRODUCT` values.
- Detects common Xiaomi ROM region suffixes from the build display/fingerprint.
- Firmware search query includes the device family, codename and detected region.
- See `REDMI_NOTE_14_4G_TEST_PLAN.md` for a hardware test checklist.

This is still not a completed ADB/Fastboot flasher. No partition write, bootloader unlock,
FRP bypass or exploit functionality is included in this build.


## Reference implementation for Android-to-Android flashing
A public Kotlin project named Leaf Flasher demonstrates USB OTG Fastboot/FastbootD,
ADB sideload and ROM payload handling. It is a separate project and is not bundled
or copied into AndroidToolByAsiman. It can be reviewed as an architectural reference:
https://github.com/devvsid/LeafFlasher

Before reusing any source, preserve its Apache-2.0 license, NOTICE and third-party
licenses. A future integration should be reviewed module-by-module and tested on
real devices rather than blindly copying a complete flasher.

## USB Host diagnostics in v0.5.0
- Requests Android USB permission per connected device using `UsbManager.requestPermission`.
- Displays VID/PID, manufacturer/product strings, device class, USB version, interface count and interface class/subclass/protocol.
- Adds heuristic labels for common ADB (`ff/66/01`) and Fastboot (`ff/66/03`) interface descriptors. These labels are informational only and are not protocol handshakes.
- Does not claim that a device is flashable, does not claim a successful ADB/Fastboot connection, and does not send USB transfers or write partitions.
- No MediaTek Preloader/BROM operation is implemented.

The USB Host permission and enumeration flow follows the Android Developers USB Host guidance: https://developer.android.com/develop/connectivity/usb/host
