package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MessageMultiple01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.WechatBotSetting
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.weixin.WeixinBotClient
import me.rerere.rikkahub.service.WeixinBotService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.RiskConfirmDialog
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Bot de WeChat 设置页.
 *
 * 功能: 开关 (启停服务) / 扫码Inicio de sesión / 助手Estado.
 * 扫码流程: 点Inicio de sesión → getQrcode → ZXing 渲染二维码 → 轮询 status → confirmed 存 token.
 */
@Composable
fun SettingWeixinBotPage(vm: SettingVM = koinViewModel()) {
    val context = LocalContext.current
    val client: WeixinBotClient = koinInject()
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var botSetting by remember(settings) { mutableStateOf(settings.wechatBotSetting) }
    LaunchedEffect(settings) { botSetting = settings.wechatBotSetting }

    fun update(newSetting: WechatBotSetting) {
        botSetting = newSetting
        vm.updateSettings(settings.copy(wechatBotSetting = newSetting))
    }

    // 扫码Inicio de sesiónEstado
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var qrContent by remember { mutableStateOf<String?>(null) }
    var loginStatus by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var showEnableRiskDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (showEnableRiskDialog) {
        RiskConfirmDialog(
            title = stringResource(R.string.risk_weixin_bot_title),
            message = stringResource(R.string.risk_weixin_bot_message),
            onConfirm = {
                showEnableRiskDialog = false
                update(botSetting.copy(enabled = true))
                WeixinBotService.start(context)
            },
            onDismiss = { showEnableRiskDialog = false }
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Bot de WeChat") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Información
            item {
                CardGroup(
                    title = { Text("Información") },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    item(
                        leadingContent = { Icon(imageVector = HugeIcons.MessageMultiple01, contentDescription = null) },
                        headlineContent = { Text("Bot de WeChat 是什么") },
                        supportingContent = { Text("Convierte tu cuenta de WeChat en una entrada a la IA: los mensajes que reciba, tuyos o de otras personas, serán respondidos por el asistente vinculado. La IA, memoria y herramientas usarán ese asistente.") }
                    )
                    item(
                        headlineContent = { Text("Asistente vinculado") },
                        supportingContent = { Text("Usar siempre el asistente actual: ${settings.getCurrentAssistant().name.ifBlank { "未命名" }}") }
                    )
                }
            }

            // 扫码Inicio de sesión
            item {
                CardGroup(
                    title = { Text("Inicio de sesión") },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    item(
                        headlineContent = { Text("Inicio de sesiónEstado") },
                        supportingContent = {
                            Text(
                                if (botSetting.botToken.isNotBlank()) {
                                    "已Inicio de sesión (Bot: ${botSetting.botId.ifBlank { "未知" }})"
                                } else {
                                    "未Inicio de sesión"
                                }
                            )
                        },
                        trailingContent = {
                            FilledTonalButton(
                                enabled = !isLoggingIn,
                                onClick = {
                                    scope.launch {
                                        isLoggingIn = true
                                        loginStatus = "Obteniendo código QR..."
                                        try {
                                            val qr = client.getQrcode(botSetting.baseUrl)
                                            qrContent = qr.qrcodeImgContent
                                            loginStatus = "Escanea con WeChat"
                                            qrBitmap = withContext(Dispatchers.Default) {
                                                runCatching { renderQrCode(qr.qrcodeImgContent, 480) }
                                                    .onFailure {
                                                        loginStatus = "No se pudo mostrar el QR; usa el enlace de abajo: ${it.message}"
                                                    }
                                                    .getOrNull()
                                            }
                                            // 轮询扫码Estado, 最多 5 分钟
                                            val deadline = System.currentTimeMillis() + 5 * 60_000
                                            var currentQrcode = qr.qrcode
                                            var refreshCount = 0
                                            var confirmed = false
                                            while (System.currentTimeMillis() < deadline && !confirmed) {
                                                val st = client.getQrcodeStatus(currentQrcode, botSetting.baseUrl)
                                                when (st.status) {
                                                    "confirmed" -> {
                                                        update(
                                                            botSetting.copy(
                                                                botToken = st.botToken ?: "",
                                                                baseUrl = st.baseUrl ?: botSetting.baseUrl,
                                                                botId = st.botId ?: "",
                                                            )
                                                        )
                                                        loginStatus = "Inicio de sesión成功!"
                                                        confirmed = true
                                                    }
                                                    "scaned" -> loginStatus = "QR escaneado; confirma en WeChat..."
                                                    "expired" -> {
                                                        refreshCount++
                                                        if (refreshCount > 3) {
                                                            loginStatus = "El QR caducó varias veces; inténtalo de nuevo"
                                                            break
                                                        }
                                                        loginStatus = "El QR caducó; actualizando..."
                                                        val newQr = client.getQrcode(botSetting.baseUrl)
                                                        currentQrcode = newQr.qrcode
                                                        qrBitmap = withContext(Dispatchers.Default) {
                                                            runCatching { renderQrCode(newQr.qrcodeImgContent, 480) }
                                                                .getOrNull()
                                                        }
                                                    }
                                                    else -> loginStatus = "Esperando escaneo..." // wait
                                                }
                                                delay(1000)
                                            }
                                            if (!confirmed && loginStatus == "Esperando escaneo...") {
                                                loginStatus = "Inicio de sesión超时"
                                            }
                                            qrBitmap = null
                                        } catch (e: Exception) {
                                            loginStatus = "Inicio de sesión失败: ${e.message ?: e::class.simpleName}"
                                        } finally {
                                            isLoggingIn = false
                                        }
                                    }
                                }
                            ) {
                                Text(if (isLoggingIn) "Inicio de sesión中..." else if (botSetting.botToken.isNotBlank()) "重新Inicio de sesión" else "扫码Inicio de sesión")
                            }
                        }
                    )
                    if (loginStatus.isNotBlank() && loginStatus != "未Inicio de sesión") {
                        item(headlineContent = { Text("Estado") }, supportingContent = { Text(loginStatus) })
                    }
                    if (botSetting.botToken.isNotBlank()) {
                        item(
                            headlineContent = { Text("SalirInicio de sesión") },
                            trailingContent = {
                                FilledTonalButton(onClick = {
                                    WeixinBotService.stop(context)
                                    update(botSetting.copy(botToken = "", botId = ""))
                                    loginStatus = "Sesión cerrada"
                                }) { Text("Salir") }
                            }
                        )
                    }
                }
            }

            // 二维码区 —— 独立顶层 item, 确保Estado变化一定可见
            if (qrContent != null || qrBitmap != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = loginStatus,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 二维码图 (白底)
                        qrBitmap?.let { bmp ->
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "微信Inicio de sesión二维码",
                                modifier = Modifier
                                    .size(240.dp)
                                    .background(Color.White)
                                    .padding(12.dp)
                            )
                        }
                        // URL 始终显示 (可点击打开)
                        qrContent?.let { url ->
                            Text(
                                text = "Si el QR no aparece, abre el enlace en el navegador:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }) { Text("Abrir enlace del QR en el navegador") }
                        }
                    }
                }
            }

            // 总开关
            item {
                CardGroup(
                    title = { Text("Ejecución") },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    item(
                        headlineContent = { Text("启用Bot de WeChat") },
                        supportingContent = { Text("开启后启动后台长轮询服务. 需先扫码Inicio de sesión.") },
                        trailingContent = {
                            Switch(
                                checked = botSetting.enabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showEnableRiskDialog = true
                                    } else {
                                        update(botSetting.copy(enabled = false))
                                        WeixinBotService.stop(context)
                                    }
                                }
                            )
                        }
                    )
                    if (botSetting.enabled && botSetting.botToken.isBlank()) {
                        item(
                            headlineContent = { Text("⚠ 尚未Inicio de sesión") },
                            supportingContent = { Text("服务需要Inicio de sesión后才能收发消息, 请先扫码Inicio de sesión") }
                        )
                    }
                }
            }
        }
    }
}

/** 用 ZXing 把字符串渲染成二维码 Bitmap. */
private fun renderQrCode(content: String, sizePx: Int): android.graphics.Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bmp = createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bmp[x, y] = if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return bmp
}
