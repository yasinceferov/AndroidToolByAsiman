# Redmi Note 14 4G — тестовый профиль

Ожидаемое семейство устройства: Redmi Note 14 4G.
Известное базовое кодовое имя: `tanzanite`.
Кодовое имя в приложении не считается подтверждённым, пока оно не совпало с фактическими `Build.DEVICE` / `Build.PRODUCT`.

## Что приложение читает
- `Build.MANUFACTURER`, `Build.MODEL`
- `Build.DEVICE`, `Build.PRODUCT`
- `Build.DISPLAY`, `Build.FINGERPRINT`
- `Build.VERSION.RELEASE`, `Build.VERSION.SDK_INT`
- `Build.VERSION.SECURITY_PATCH`
- `Build.SUPPORTED_ABIS`

Регион Xiaomi определяется по строке сборки, если присутствует региональный суффикс:
- `MIXM` — Global
- `EUXM` — EEA
- `RUXM` — Russia
- `IDXM` — Indonesia
- `TWXM` — Taiwan
- `TRXM` — Turkey
- `INXM` — India

Если регион не распознан, приложение должно просить сверить полный номер сборки вручную, а не угадывать.

## План аппаратных тестов
1. Запустить APK на Redmi Note 14 4G и сохранить экран «Обзор».
2. Сверить `Build.DEVICE` и `Build.PRODUCT` с доверенным списком именно для своей региональной версии.
3. Проверить, что регион сборки правильно извлечён из `Build.DISPLAY` или `Build.FINGERPRINT`.
4. Проверить USB OTG с USB-флешкой и вторым Android-устройством.
5. Проверить ADB authorization: на целевом телефоне включить USB debugging и явно подтвердить RSA-ключ хоста.
6. Перевести целевое устройство в Fastboot вручную и проверить только обнаружение/чтение переменных до реализации записи.
7. Не выполнять `flash`, `erase`, `format`, `unlock` или OEM-команды на тестовом устройстве до появления проверок модели, разделов, резервной копии и подтверждений.

## Совместимость
Универсальный Android APK не может гарантировать USB OTG на каждом телефоне: нужны USB Host hardware, корректная роль OTG, кабель/питание и разрешение USB. ADB и Fastboot — разные протоколы и режимы USB. Для некоторых моделей доступны только фирменные процедуры.
