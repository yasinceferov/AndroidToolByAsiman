package com.asiman.androidtool

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.app.PendingIntent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbConstants
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.security.MessageDigest

private const val ACTION_USB_PERMISSION = "com.asiman.androidtool.USB_PERMISSION"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AndroidToolApp(this) }
    }
}


data class DeviceInfo(val label: String, val value: String)

private fun detectedXiaomiRegion(): String {
    val source = "${Build.DISPLAY} ${Build.FINGERPRINT}".uppercase()
    return when {
        "EUXM" in source -> "EEA / Европа"
        "MIXM" in source -> "Global"
        "RUXM" in source -> "Россия"
        "IDXM" in source -> "Индонезия"
        "TWXM" in source -> "Тайвань"
        "TRXM" in source -> "Турция"
        "INXM" in source -> "Индия"
        else -> "Не определён — проверь вручную"
    }
}

private fun isRedmiNote14FourG(): Boolean {
    val identity = "${Build.MANUFACTURER} ${Build.MODEL} ${Build.DEVICE} ${Build.PRODUCT}".lowercase()
    val xiaomiBrand = "xiaomi" in identity || "redmi" in identity
    val exactCodename = "tanzanite" in identity
    val modelLooksLike4g = "redmi note 14" in identity &&
        "5g" !in identity && "pro" !in identity && "pro+" !in identity
    return xiaomiBrand && (exactCodename || modelLooksLike4g)
}

private fun inferredCodename(): String {
    val device = Build.DEVICE.lowercase()
    val product = Build.PRODUCT.lowercase()
    return when {
        "tanzanite" in device -> device
        "tanzanite" in product -> product
        isRedmiNote14FourG() -> "tanzanite (ожидаемый профиль; сверить с Build.DEVICE)"
        else -> "Не определён автоматически"
    }
}

private fun readDeviceInfo(context: Context): List<DeviceInfo> {
    val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val stat = StatFs(Environment.getDataDirectory().path)
    val total = stat.totalBytes / (1024L * 1024L * 1024L)
    val free = stat.availableBytes / (1024L * 1024L * 1024L)
    return listOf(
        DeviceInfo("Производитель", Build.MANUFACTURER),
        DeviceInfo("Модель", Build.MODEL),
        DeviceInfo("Код устройства", Build.DEVICE),
        DeviceInfo("Продукт", Build.PRODUCT),
        DeviceInfo("Android", Build.VERSION.RELEASE),
        DeviceInfo("SDK", Build.VERSION.SDK_INT.toString()),
        DeviceInfo("Сборка", Build.DISPLAY),
        DeviceInfo("Отпечаток сборки", Build.FINGERPRINT),
        DeviceInfo("Патч безопасности", Build.VERSION.SECURITY_PATCH.ifBlank { "Недоступен" }),
        DeviceInfo("Архитектура", Build.SUPPORTED_ABIS.joinToString()),
        DeviceInfo("Уровень батареи", if (level in 0..100) "$level%" else "Недоступно"),
        DeviceInfo("Память", "$free ГБ свободно из $total ГБ")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidToolApp(context: Context) {
    var selected by remember { mutableStateOf("Обзор") }
    var refresh by remember { mutableIntStateOf(0) }
    val info = remember(refresh) { readDeviceInfo(context) }
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    var usbRefresh by remember { mutableIntStateOf(0) }
    var usbMessage by remember { mutableStateOf("Нажмите «Обновить USB» после подключения телефона.") }
    val usbDevices = remember(refresh, usbRefresh) { usbManager.deviceList.values.toList() }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == ACTION_USB_PERMISSION) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null) {
                        usbMessage = if (granted) "Разрешение выдано для ${device.deviceName}. Доступ к интерфейсам открыт для диагностики." else "Разрешение USB не выдано."
                    }
                    usbRefresh++
                }
            }
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    fun requestUsbPermission(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            context, device.deviceId,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(device, permissionIntent)
        usbMessage = "Ожидание разрешения Android для ${device.deviceName}…"
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("AndroidToolByAsiman")
                            Text("Диагностика • прошивки • USB", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { refresh++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf("Обзор", "USB", "Прошивки", "Прошивка", "Безопасность").forEach { item ->
                        NavigationBarItem(
                            selected = selected == item,
                            onClick = { selected = item },
                            icon = {
                                Icon(
                                    when (item) {
                                        "USB" -> Icons.Default.Usb
                                        "Прошивки" -> Icons.Default.SystemUpdate
                                        "Прошивка" -> Icons.Default.Build
                                        "Безопасность" -> Icons.Default.Security
                                        else -> Icons.Default.PhoneAndroid
                                    }, contentDescription = item
                                )
                            },
                            label = { Text(item) }
                        )
                    }
                }
            }
        ) { padding ->
            when (selected) {
                "Обзор" -> Overview(info, Modifier.padding(padding))
                "USB" -> UsbScreen(
                    usbDevices = usbDevices,
                    usbManager = usbManager,
                    message = usbMessage,
                    onRefresh = { usbRefresh++ },
                    onRequestPermission = { requestUsbPermission(it) },
                    modifier = Modifier.padding(padding)
                )
                "Прошивки" -> FirmwareScreen(context, info, Modifier.padding(padding))
                "Прошивка" -> FlashPrepScreen(context, Modifier.padding(padding))
                else -> SecurityScreen(info, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun Overview(info: List<DeviceInfo>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Информация об устройстве", style = MaterialTheme.typography.headlineSmall) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Профиль тестирования", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isRedmiNote14FourG()) "Redmi Note 14 4G обнаружен по системным идентификаторам."
                        else "Универсальный режим: устройство не совпало с тестовым профилем Redmi Note 14 4G."
                    )
                    Text("Определённый codename: ${inferredCodename()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(info) { row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(row.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text(
                "Примечание: Android ограничивает доступ к некоторым сведениям о загрузчике, root и разделах устройства.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UsbScreen(
    usbDevices: List<UsbDevice>,
    usbManager: UsbManager,
    message: String,
    onRefresh: () -> Unit,
    onRequestPermission: (UsbDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("USB Host / OTG диагностика", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Показываются устройства и USB-интерфейсы, обнаруженные Android. Для чтения дескрипторов приложение запрашивает отдельное разрешение.") }
        item {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Обновить USB")
            }
        }
        item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        if (usbDevices.isEmpty()) {
            item { Text("Устройства не обнаружены. Проверьте OTG-переходник и используйте кабель с передачей данных.") }
        }
        items(usbDevices, key = { it.deviceName }) { device ->
            val hasPermission = usbManager.hasPermission(device)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB-устройство", style = MaterialTheme.typography.titleMedium)
                    Text("${device.manufacturerName ?: "Производитель неизвестен"} • ${device.deviceName}", style = MaterialTheme.typography.bodySmall)
                    Text("VID: 0x${device.vendorId.toString(16).uppercase().padStart(4, '0')}   PID: 0x${device.productId.toString(16).uppercase().padStart(4, '0')}")
                    Text("USB device class: ${device.deviceClass} / subclass ${device.deviceSubclass} / protocol ${device.deviceProtocol}")
                    Text("Версия USB: ${device.version} • Интерфейсов: ${device.interfaceCount}")
                    if (!hasPermission) {
                        OutlinedButton(onClick = { onRequestPermission(device) }, modifier = Modifier.fillMaxWidth()) { Text("Запросить разрешение Android") }
                    } else {
                        Text("Разрешение: выдано", color = MaterialTheme.colorScheme.primary)
                        for (i in 0 until device.interfaceCount) {
                            val usbInterface = device.getInterface(i)
                            val kind = when {
                                usbInterface.interfaceClass == 0xff && usbInterface.interfaceSubclass == 66 && usbInterface.interfaceProtocol == 3 -> "Похоже на Fastboot (vendor-specific)"
                                usbInterface.interfaceClass == 0xff && usbInterface.interfaceSubclass == 66 && usbInterface.interfaceProtocol == 1 -> "Похоже на ADB (vendor-specific)"
                                usbInterface.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE -> "USB Mass Storage"
                                usbInterface.interfaceClass == UsbConstants.USB_CLASS_COMM -> "USB Communication"
                                else -> "Другой интерфейс"
                            }
                            Text("Интерфейс $i: class ${usbInterface.interfaceClass}, subclass ${usbInterface.interfaceSubclass}, protocol ${usbInterface.interfaceProtocol} — $kind", style = MaterialTheme.typography.bodySmall)
                            Text("Endpoints: ${usbInterface.endpointCount}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Важно: распознавание ADB/Fastboot по USB-дескрипторам предварительное. Эта версия не открывает интерфейсы, не отправляет команды и не меняет разделы. Устройства MediaTek Preloader/BROM специально не классифицируются как Fastboot.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FirmwareScreen(context: Context, info: List<DeviceInfo>, modifier: Modifier = Modifier) {
    val model = info.firstOrNull { it.label == "Модель" }?.value ?: "Android"
    val device = info.firstOrNull { it.label == "Код устройства" }?.value ?: ""
    val xiaomi = isRedmiNote14FourG()
    val codename = inferredCodename()
    val region = detectedXiaomiRegion()

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Поиск совместимых прошивок", style = MaterialTheme.typography.headlineSmall)
        Text("Поиск использует модель, код устройства и регион сборки. Совпадение названия само по себе не подтверждает совместимость.")
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Профиль текущего телефона", style = MaterialTheme.typography.titleMedium)
                Text("Модель: $model")
                Text("Build.DEVICE: $device")
                if (xiaomi) {
                    Text("Профиль: Redmi Note 14 4G")
                    Text("Codename: $codename")
                    Text("Регион ПО: $region")
                } else {
                    Text("Профиль Xiaomi Redmi Note 14 4G не подтверждён.")
                }
            }
        }
        Button(onClick = {
            val queryText = if (xiaomi) {
                "Redmi Note 14 4G tanzanite $region official Xiaomi firmware ROM"
            } else {
                "$model $device official firmware ROM"
            }
            val query = android.net.Uri.encode(queryText)
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.google.com/search?q=$query")
            )
            context.startActivity(intent)
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Найти подходящие прошивки")
        }
        if (xiaomi) {
            OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://new.c.mi.com/global/")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Открыть сообщество Xiaomi") }
        }
        Text(
            "Для Redmi Note 14 4G известен codename tanzanite. Региональные пакеты различаются. Сверяйте полный номер модели, codename, регион, тип пакета (Recovery/Fastboot) и контрольную сумму перед установкой.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun FlashPrepScreen(context: Context, modifier: Modifier = Modifier) {
    var fileName by remember { mutableStateOf("Файл не выбран") }
    var sha256 by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Выберите образ прошивки для проверки.") }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val name = uri.lastPathSegment ?: "Выбранный файл"
                val digest = MessageDigest.getInstance("SHA-256")
                var bytesRead = 0L
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(1024 * 64)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        bytesRead += count
                    }
                } ?: throw IllegalStateException("Не удалось открыть файл")
                fileName = name
                fileSize = "${"%.2f".format(bytesRead / (1024.0 * 1024.0))} MB"
                sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                message = "SHA-256 рассчитан. Сравните его с контрольной суммой из доверенного источника."
            } catch (e: Exception) {
                message = "Ошибка чтения файла: ${e.message ?: "неизвестная ошибка"}"
                sha256 = ""
            }
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Flash Manager", style = MaterialTheme.typography.headlineSmall)
        Text("Подготовка образа к прошивке с телефона через USB OTG.")
        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Выбрать файл прошивки")
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Выбранный файл", style = MaterialTheme.typography.titleMedium)
                Text(fileName)
                if (fileSize.isNotBlank()) Text("Размер: $fileSize")
                if (sha256.isNotBlank()) {
                    Text("SHA-256", style = MaterialTheme.typography.labelLarge)
                    Text(sha256, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SHA-256", sha256))
                    }) { Text("Копировать SHA-256") }
                }
            }
        }
        Text(message, style = MaterialTheme.typography.bodySmall)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Перед прошивкой", style = MaterialTheme.typography.titleMedium)
                Text("• Сверьте точную модель и codename.")
                Text("• Проверьте SHA-256 по официальному источнику.")
                Text("• Зарядите оба телефона и сохраните резервную копию.")
                Text("• Разблокировка загрузчика обычно стирает пользовательские данные.")
                Text("• Не прошивайте образ от другой модели или региона.")
            }
        }
        Text(
            "Передача образа в разделы устройства и выполнение Fastboot flash ещё не реализованы. Этот экран безопасно выбирает файл и проверяет его целостность; реальная запись требует отдельного USB Fastboot Host-модуля и тестирования на конкретном оборудовании.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SecurityScreen(info: List<DeviceInfo>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val patch = android.os.Build.VERSION.SECURITY_PATCH
        Text("Безопасность и уязвимости", style = MaterialTheme.typography.headlineSmall)
        Text("Уровень патча безопасности: $patch")
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Локальная проверка", style = MaterialTheme.typography.titleMedium)
                Text("Проверка показывает версию Android и установленный патч безопасности. Для оценки CVE потребуется отдельная база уязвимостей и сопоставление по версии, сборке и патчу. Приложение не эксплуатирует уязвимости и не обходит блокировки.")
            }
        }
        Text("Не используйте найденные уязвимости для доступа к устройствам без разрешения владельца.")
    }
}
