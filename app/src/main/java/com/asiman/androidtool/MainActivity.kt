package com.asiman.androidtool

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.app.PendingIntent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.hardware.usb.UsbConstants
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.compose.runtime.rememberCoroutineScope
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
import ernest.ascrcpy.adb.AdbClient
import ernest.ascrcpy.adb.DefaultAdbClient
import ernest.ascrcpy.adb.transport.UsbAdbTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ACTION_USB_PERMISSION = "com.asiman.androidtool.USB_PERMISSION"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AndroidToolApp(this) }
    }
}


data class DeviceInfo(val label: String, val value: String)

private fun detectedXiaomiRegion(): String = detectedXiaomiRegion("${Build.DISPLAY} ${Build.FINGERPRINT}")

private fun detectedXiaomiRegion(sourceText: String): String {
    val source = sourceText.uppercase()
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


private fun usbVendorName(vendorId: Int): String = when (vendorId) {
    0x2717 -> "Xiaomi (VID известен)"
    0x04e8 -> "Samsung"
    0x18d1 -> "Google"
    0x12d1 -> "Huawei"
    0x0e8d -> "MediaTek"
    0x22d9 -> "OPPO"
    0x2a70 -> "OnePlus"
    0x22b8 -> "Motorola"
    else -> "Неизвестен по VID"
}

private fun usbDeviceKind(vendorId: Int): String = when (vendorId) {
    0x2717, 0x04e8, 0x18d1, 0x12d1, 0x22d9, 0x2a70, 0x22b8 -> "возможен телефон/планшет (по VID)"
    0x0e8d -> "MediaTek USB; это может быть Preloader/BROM, не Fastboot"
    else -> "не определён; проверьте product и интерфейсы"
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

private fun remoteValue(info: List<DeviceInfo>, label: String): String =
    info.firstOrNull { it.label == label }?.value.orEmpty()

private fun isRemoteRedmiNote14FourG(info: List<DeviceInfo>): Boolean {
    val identity = "${remoteValue(info, "Производитель")} ${remoteValue(info, "Модель")} ${remoteValue(info, "Код устройства")} ${remoteValue(info, "Продукт")}".lowercase()
    return ("xiaomi" in identity || "redmi" in identity) &&
        (("tanzanite" in identity) || ("redmi note 14" in identity && "5g" !in identity && "pro" !in identity))
}

private fun remoteCodename(info: List<DeviceInfo>): String =
    remoteValue(info, "Код устройства").ifBlank { "Не определён" }

private suspend fun readConnectedAndroid(client: AdbClient): List<DeviceInfo> {
    suspend fun prop(key: String): String = client.shell("getprop $key").text().trim().ifBlank { "Недоступно" }
    val manufacturer = prop("ro.product.manufacturer")
    val model = prop("ro.product.model")
    val device = prop("ro.product.device")
    val product = prop("ro.product.name")
    val release = prop("ro.build.version.release")
    val sdk = prop("ro.build.version.sdk")
    val display = prop("ro.build.display.id")
    val fingerprint = prop("ro.build.fingerprint")
    val patch = prop("ro.build.version.security_patch")
    val abis = prop("ro.product.cpu.abilist")
    val locked = prop("ro.boot.flash.locked")
    val vbmeta = prop("ro.boot.vbmeta.device_state")
    val verified = prop("ro.boot.verifiedbootstate")
    val batteryText = client.shell("dumpsys battery").text()
    val batteryLevel = Regex("(?m)^\\s*level:\\s*(\\d+)").find(batteryText)?.groupValues?.getOrNull(1)
    val dfText = client.shell("df -k /data").text().lines().filter { it.isNotBlank() }
    val df = dfText.lastOrNull()?.trim()?.split(Regex("\\s+"))
    val storage = if (df != null && df.size >= 4) {
        val totalKb = df[1].toLongOrNull()
        val freeKb = df[3].toLongOrNull()
        if (totalKb != null && freeKb != null) "${freeKb / 1_048_576} ГБ свободно из ${totalKb / 1_048_576} ГБ" else "Недоступно"
    } else "Недоступно"
    return listOf(
        DeviceInfo("Производитель", manufacturer), DeviceInfo("Модель", model),
        DeviceInfo("Код устройства", device), DeviceInfo("Продукт", product),
        DeviceInfo("Android", release), DeviceInfo("SDK", sdk),
        DeviceInfo("Сборка", display), DeviceInfo("Отпечаток сборки", fingerprint),
        DeviceInfo("Патч безопасности", patch), DeviceInfo("Архитектура", abis),
        DeviceInfo("Уровень батареи", batteryLevel?.let { "$it%" } ?: "Недоступно"),
        DeviceInfo("Память", storage), DeviceInfo("Bootloader locked (ro.boot.flash.locked)", locked),
        DeviceInfo("VBMeta device state", vbmeta), DeviceInfo("Verified boot state", verified)
    )
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
    var usbMessage by remember { mutableStateOf("Подключите второй телефон через OTG и включите USB-отладку на нём.") }
    val usbDevices = remember(refresh, usbRefresh) { usbManager.deviceList.values.toList() }
    val adbUsbDevices = remember(refresh, usbRefresh) { UsbAdbTransport.discover(usbManager) }
    val coroutineScope = rememberCoroutineScope()
    var adbClient by remember { mutableStateOf<AdbClient?>(null) }
    var remoteInfo by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var adbDeviceName by remember { mutableStateOf("") }
    var adbBusy by remember { mutableStateOf(false) }
    var adbMessage by remember { mutableStateOf("ADB не подключён. Для чтения данных включите USB debugging на втором телефоне.") }

    DisposableEffect(adbClient) {
        onDispose { adbClient?.close() }
    }

    fun connectAdb(device: UsbDevice) {
        if (adbBusy) return
        coroutineScope.launch {
            adbBusy = true
            adbMessage = "Подключение ADB к ${device.deviceName}… Подтвердите RSA-ключ на втором телефоне, если появится запрос."
            val candidate = DefaultAdbClient.factory(context.applicationContext).create()
            try {
                val details = withContext(Dispatchers.IO) {
                    candidate.connectUsb(device)
                    readConnectedAndroid(candidate)
                }
                adbClient?.close()
                adbClient = candidate
                remoteInfo = details
                adbDeviceName = device.deviceName
                adbMessage = "ADB подключён. Данные ниже получены со второго телефона, а не с Redmi-хоста."
            } catch (error: Exception) {
                candidate.close()
                adbMessage = "ADB не подключился: ${error.message ?: "неизвестная ошибка"}. Проверьте USB debugging, кабель и RSA-подтверждение."
            } finally {
                adbBusy = false
                usbRefresh++
            }
        }
    }

    fun disconnectAdb() {
        adbClient?.close()
        adbClient = null
        remoteInfo = emptyList()
        adbDeviceName = ""
        adbMessage = "ADB отключён."
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_USB_PERMISSION -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (device != null) {
                            usbMessage = if (granted) "Разрешение выдано для ${device.deviceName}. USB-дескрипторы доступны." else "Разрешение USB не выдано."
                        }
                        usbRefresh++
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        usbMessage = "USB-устройство подключено. Список обновлён."
                        usbRefresh++
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        usbMessage = "USB-устройство отключено. Список обновлён."
                        if (adbDeviceName.isNotBlank()) disconnectAdb()
                        usbRefresh++
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
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
                    adbMessage = adbMessage,
                    adbBusy = adbBusy,
                    adbDevices = adbUsbDevices,
                    remoteInfo = remoteInfo,
                    connectedDeviceName = adbDeviceName,
                    onConnectAdb = { connectAdb(it) },
                    onDisconnectAdb = { disconnectAdb() },
                    onRefresh = { usbRefresh++ },
                    onRequestPermission = { requestUsbPermission(it) },
                    modifier = Modifier.padding(padding)
                )
                "Прошивки" -> FirmwareScreen(context, info, remoteInfo, Modifier.padding(padding))
                "Прошивка" -> FlashPrepScreen(context, Modifier.padding(padding))
                else -> SecurityScreen(context, info, remoteInfo, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun Overview(info: List<DeviceInfo>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Телефон, на котором запущено приложение", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Это сведения о самом Asiman-устройстве. Подключённый по OTG телефон показывается отдельно на вкладке USB.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    adbMessage: String,
    adbBusy: Boolean,
    adbDevices: List<UsbDevice>,
    remoteInfo: List<DeviceInfo>,
    connectedDeviceName: String,
    onConnectAdb: (UsbDevice) -> Unit,
    onDisconnectAdb: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: (UsbDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Подключённый телефон / USB OTG", style = MaterialTheme.typography.headlineSmall) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Важно: это другое устройство", style = MaterialTheme.typography.titleMedium)
                    Text("Здесь отображается USB-устройство на другом конце OTG-кабеля. Для получения модели, Android, батареи и памяти включите USB-отладку на подключённом телефоне, выдайте USB-разрешение и нажмите «Подключить телефон через ADB». На втором телефоне подтвердите RSA-ключ.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Список USB-устройств, обнаруженных Android USB Host API. При подключении или отключении список обновляется автоматически.") }
        item {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Обновить USB")
            }
        }
        item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Подключённый телефон (ADB)", style = MaterialTheme.typography.titleLarge)
                    Text(adbMessage, style = MaterialTheme.typography.bodySmall)
                    if (remoteInfo.isNotEmpty()) {
                        Text("USB: $connectedDeviceName", style = MaterialTheme.typography.labelSmall)
                        remoteInfo.forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(row.value, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedButton(onClick = onDisconnectAdb, modifier = Modifier.fillMaxWidth()) { Text("Отключить ADB") }
                    } else {
                        Text("ADB читает свойства именно подключённого устройства после USB-разрешения и подтверждения RSA-ключа на нём.")
                    }
                }
            }
        }
        if (usbDevices.isEmpty()) {
            item { Text("Устройства не обнаружены. Проверьте OTG-переходник и используйте кабель с передачей данных.") }
        }
        items(usbDevices, key = { it.deviceName }) { device ->
            val hasPermission = usbManager.hasPermission(device)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB-устройство", style = MaterialTheme.typography.titleMedium)
                    Text("USB-производитель: ${device.manufacturerName?.toString()?.takeIf { it.isNotBlank() } ?: usbVendorName(device.vendorId)}", style = MaterialTheme.typography.bodySmall)
                    Text("USB product: ${device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "не сообщается устройством"}", style = MaterialTheme.typography.bodySmall)
                    Text("Путь USB: ${device.deviceName}", style = MaterialTheme.typography.labelSmall)
                    Text("VID: 0x${device.vendorId.toString(16).uppercase().padStart(4, '0')}   PID: 0x${device.productId.toString(16).uppercase().padStart(4, '0')}")
                    Text("Предполагаемый тип: ${usbDeviceKind(device.vendorId)}", style = MaterialTheme.typography.bodySmall)
                    Text("USB device class: ${device.deviceClass} / subclass ${device.deviceSubclass} / protocol ${device.deviceProtocol}")
                    Text("Версия USB: ${device.version} • Интерфейсов: ${device.interfaceCount}")
                    val adbCapable = adbDevices.any { it.deviceId == device.deviceId }
                    if (!hasPermission) {
                        OutlinedButton(onClick = { onRequestPermission(device) }, modifier = Modifier.fillMaxWidth()) { Text("Запросить разрешение Android") }
                    } else {
                        if (adbCapable) {
                            Button(onClick = { onConnectAdb(device) }, enabled = !adbBusy, modifier = Modifier.fillMaxWidth()) {
                                Text(if (adbBusy) "Подключение ADB…" else "Подключить телефон через ADB")
                            }
                        } else {
                            Text("Стандартный ADB-интерфейс не обнаружен. Включите USB-отладку на подключённом телефоне и переподключите кабель.", style = MaterialTheme.typography.bodySmall)
                        }
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
                            for (endpointIndex in 0 until usbInterface.endpointCount) {
                                val endpoint = usbInterface.getEndpoint(endpointIndex)
                                val direction = if (endpoint.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                                Text("  Endpoint $endpointIndex: $direction • type ${endpoint.type} • max packet ${endpoint.maxPacketSize}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "ADB получает свойства удалённого Android только после авторизации. Fastboot здесь пока определяется по USB-интерфейсу без выполнения команд; прошивка разделов и обход защиты загрузчика не реализованы. MediaTek Preloader/BROM не является Fastboot.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FirmwareScreen(context: Context, info: List<DeviceInfo>, remoteInfo: List<DeviceInfo>, modifier: Modifier = Modifier) {
    val usingRemote = remoteInfo.isNotEmpty()
    val source = if (usingRemote) remoteInfo else info
    val model = remoteValue(source, "Модель").ifBlank { "Android" }
    val device = remoteValue(source, "Код устройства")
    val xiaomi = if (usingRemote) isRemoteRedmiNote14FourG(source) else isRedmiNote14FourG()
    val codename = if (usingRemote) remoteCodename(source) else inferredCodename()
    val region = if (usingRemote) detectedXiaomiRegion("${remoteValue(source, "Сборка")} ${remoteValue(source, "Отпечаток сборки")}") else detectedXiaomiRegion()

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Поиск совместимых прошивок", style = MaterialTheme.typography.headlineSmall)
        Text("Поиск использует модель, код устройства и регион сборки. Совпадение названия само по себе не подтверждает совместимость.")
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (usingRemote) "Профиль подключённого телефона (ADB)" else "Профиль телефона с приложением", style = MaterialTheme.typography.titleMedium)
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
                "$model $codename $region official Xiaomi firmware ROM"
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
private fun SecurityScreen(
    context: Context,
    info: List<DeviceInfo>,
    remoteInfo: List<DeviceInfo>,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val localPatch = info.firstOrNull { it.label == "Патч безопасности" }?.value ?: "Недоступен"
        Text("Безопасность и состояние загрузчика", style = MaterialTheme.typography.headlineSmall)
        Text("Телефон с приложением — патч безопасности: $localPatch")
        if (remoteInfo.isNotEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Подключённый телефон (ADB)", style = MaterialTheme.typography.titleMedium)
                    Text("Модель: ${remoteValue(remoteInfo, "Модель")}")
                    Text("Патч безопасности: ${remoteValue(remoteInfo, "Патч безопасности")}")
                    Text("ro.boot.flash.locked: ${remoteValue(remoteInfo, "Bootloader locked (ro.boot.flash.locked)")}")
                    Text("ro.boot.vbmeta.device_state: ${remoteValue(remoteInfo, "VBMeta device state")}")
                    Text("ro.boot.verifiedbootstate: ${remoteValue(remoteInfo, "Verified boot state")}")
                    Text("Это диагностические свойства Android. Пустое или недоступное значение не доказывает, что загрузчик заблокирован или разблокирован.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text("Подключите телефон через USB и авторизуйте ADB, чтобы прочитать его доступные свойства загрузчика.")
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Локальная проверка", style = MaterialTheme.typography.titleMedium)
                Text("Показываются версия Android и установленный патч безопасности. Для полноценной оценки CVE потребуется отдельная база уязвимостей и сопоставление по версии, сборке и патчу.")
            }
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Разблокировка загрузчика Xiaomi", style = MaterialTheme.typography.titleMedium)
                Text("Обход блокировки, FRP или принудительное снятие защиты не добавляются. Для своего устройства используйте официальный процесс Xiaomi и Mi Unlock. Разблокировка может стереть все пользовательские данные и снизить защиту телефона.")
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.mi.com/global/support/faq/details/KA-07238/")))
                }, modifier = Modifier.fillMaxWidth()) { Text("Официальная инструкция Xiaomi") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://en.miui.com/unlock/download_en.html")))
                }, modifier = Modifier.fillMaxWidth()) { Text("Страница Mi Unlock для ПК") }
            }
        }
        Text("Приложение не эксплуатирует уязвимости и не обходит блокировки. Не используйте диагностику для доступа к устройствам без разрешения владельца.", style = MaterialTheme.typography.bodySmall)
    }
}
