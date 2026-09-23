package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Message01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.QqBotSetting
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.QqBotService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.RiskConfirmDialog
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

/**
 * QQ Bot 设置页.
 *
 * 比微信页简单: 不用扫码, 直接填 AppID + AppSecret (在 q.qq.com 注册机器人后获得).
 * 固定用当前助手.
 */
@Composable
fun SettingQqBotPage(vm: SettingVM = koinViewModel()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var botSetting by remember(settings) { mutableStateOf(settings.qqBotSetting) }
    LaunchedEffect(settings) { botSetting = settings.qqBotSetting }

    fun update(newSetting: QqBotSetting) {
        botSetting = newSetting
        vm.updateSettings(settings.copy(qqBotSetting = newSetting))
    }

    var showEnableRiskDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (showEnableRiskDialog) {
        RiskConfirmDialog(
            title = stringResource(R.string.risk_qq_bot_title),
            message = stringResource(R.string.risk_qq_bot_message),
            onConfirm = {
                showEnableRiskDialog = false
                update(botSetting.copy(enabled = true))
                QqBotService.start(context)
            },
            onDismiss = { showEnableRiskDialog = false }
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("QQ Bot") },
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
                        leadingContent = { Icon(imageVector = HugeIcons.Message01, contentDescription = null) },
                        headlineContent = { Text("Qué es QQ Bot") },
                        supportingContent = { Text("Convierte tu bot de QQ en una entrada a la IA: los mensajes privados recibidos serán respondidos por el asistente actual. Sólo procesa chats privados.") }
                    )
                    item(
                        headlineContent = { Text("Cómo obtener AppID y Secret") },
                        supportingContent = { Text("1. Regístrate como desarrollador en q.qq.com y crea un bot\n2. Busca AppID y AppSecret en la administración del bot\n3. Cópialos abajo") }
                    )
                    item(
                        headlineContent = { Text("Asistente vinculado") },
                        supportingContent = { Text("Usar siempre el asistente actual: ${settings.getCurrentAssistant().name.ifBlank { "Sin nombre" }}") }
                    )
                }
            }

            // 凭证
            item {
                CardGroup(
                    title = { Text("Credenciales del bot") },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    item(
                        headlineContent = { Text("AppID") },
                        supportingContent = {
                            OutlinedTextField(
                                value = botSetting.appId,
                                onValueChange = { update(botSetting.copy(appId = it.trim())) },
                                placeholder = { Text("p. ej. 102345678") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = MaterialTheme.shapes.small,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                )
                            )
                        }
                    )
                    item(
                        headlineContent = { Text("AppSecret") },
                        supportingContent = {
                            OutlinedTextField(
                                value = botSetting.appSecret,
                                onValueChange = { update(botSetting.copy(appSecret = it.trim())) },
                                placeholder = { Text("Clave del bot") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = MaterialTheme.shapes.small,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                )
                            )
                        }
                    )
                }
            }

            // 开关
            item {
                CardGroup(
                    title = { Text("Ejecución") },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    item(
                        headlineContent = { Text("Activar QQ Bot") },
                        supportingContent = { Text("Al activarlo, se abrirá una conexión WebSocket para escuchar mensajes privados. Primero debes indicar AppID y Secret.") },
                        trailingContent = {
                            Switch(
                                checked = botSetting.enabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showEnableRiskDialog = true
                                    } else {
                                        update(botSetting.copy(enabled = false))
                                        QqBotService.stop(context)
                                    }
                                }
                            )
                        }
                    )
                    if (botSetting.enabled && (botSetting.appId.isBlank() || botSetting.appSecret.isBlank())) {
                        item(
                            headlineContent = { Text("⚠ Faltan credenciales") },
                            supportingContent = { Text("Introduce AppID y AppSecret antes de activarlo") }
                        )
                    }
                    if (botSetting.enabled) {
                        item(
                            headlineContent = { Text("Notas de ejecución") },
                            supportingContent = { Text("El token se renueva automáticamente. Las respuestas deben enviarse dentro de los 5 minutos posteriores a recibir el mensaje.") }
                        )
                    }
                }
            }
        }
    }
}
