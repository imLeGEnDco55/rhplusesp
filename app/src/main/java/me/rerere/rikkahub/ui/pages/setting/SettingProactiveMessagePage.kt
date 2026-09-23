package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.RiskConfirmDialog
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.service.ProactiveMessageService
import me.rerere.rikkahub.data.service.ProactiveMessageWorker
import org.koin.androidx.compose.koinViewModel

/**
 * AI 主动发消息设置页.
 *
 * 功能: 总开关（带风险确认）/ 触发间隔范围 / 助手选择 / 精确闹钟与Optimización de batería指引.
 */
@Composable
fun SettingProactiveMessagePage(vm: SettingVM = koinViewModel()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showProactiveRiskDialog by remember { mutableStateOf(false) }

    if (showProactiveRiskDialog) {
        RiskConfirmDialog(
            title = stringResource(R.string.risk_proactive_message_title),
            message = stringResource(R.string.risk_proactive_message_message),
            onConfirm = {
                showProactiveRiskDialog = false
                val newSetting = settings.proactiveMessageSetting.copy(enabled = true)
                vm.updateSettings(settings.copy(proactiveMessageSetting = newSetting))
                ProactiveMessageService.triggerNow(context, newSetting)
            },
            onDismiss = { showProactiveRiskDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Mensajes proactivos") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                // remember/LaunchedEffect 必须处于 Composable 上下文（LazyColumn 的 item{} 内），
                // CardGroup 的 content lambda 是非 Composable 的 DSL 收集器，不能放状态
                var nextTime by remember { mutableStateOf(ProactiveMessageService.getNextTriggerTime(context)) }
                LaunchedEffect(settings.proactiveMessageSetting.enabled) {
                    while (true) {
                        kotlinx.coroutines.delay(10_000L)
                        nextTime = ProactiveMessageService.getNextTriggerTime(context)
                    }
                }
                CardGroup {
                    item(
                        headlineContent = { Text("Activar mensajes proactivos") },
                        supportingContent = { Text("Al activarlo, la IA enviará un mensaje inmediatamente y después seguirá el intervalo configurado") },
                        trailingContent = {
                            Switch(
                                checked = settings.proactiveMessageSetting.enabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showProactiveRiskDialog = true
                                    } else {
                                        val newSetting = settings.proactiveMessageSetting.copy(enabled = false)
                                        vm.updateSettings(settings.copy(proactiveMessageSetting = newSetting))
                                        ProactiveMessageService.cancel(context)
                                    }
                                }
                            )
                        }
                    )
                    if (settings.proactiveMessageSetting.enabled) {
                        item(
                            headlineContent = { Text("Próxima activación") },
                            supportingContent = {
                                val currentTime = System.currentTimeMillis()
                                val triggerTime = nextTime
                                if (triggerTime != null && triggerTime > currentTime) {
                                    val remaining = triggerTime - currentTime
                                    val remainMinutes = remaining / 60_000
                                    val remainSeconds = (remaining % 60_000) / 1000
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                    Text("${sdf.format(java.util.Date(triggerTime))} (faltan ${remainMinutes} min ${remainSeconds} s)")
                                } else {
                                    Text("Esperando programación...")
                                }
                            }
                        )
                    }
                    item(
                        headlineContent = { Text("Usar asistente") },
                        supportingContent = { Text("Usar preferentemente el asistente actual: ${settings.getCurrentAssistant().name.ifBlank { "Sin nombre" }}") }
                    )
                }
            }
            item {
                CardGroup {
                    item(
                        headlineContent = { Text("Intervalo mínimo (minutos)") },
                        supportingContent = {
                            OutlinedTextField(
                                value = settings.proactiveMessageSetting.minIntervalMinutes.toString(),
                                onValueChange = { value ->
                                    val minutes = value.toIntOrNull()
                                    if (minutes != null && minutes > 0) {
                                        vm.updateSettings(
                                            settings.copy(
                                                proactiveMessageSetting = settings.proactiveMessageSetting.copy(
                                                    minIntervalMinutes = minutes
                                                )
                                            )
                                        )
                                    }
                                },
                                placeholder = { Text("30") },
                                singleLine = true,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("Intervalo máximo (minutos)") },
                        supportingContent = {
                            OutlinedTextField(
                                value = settings.proactiveMessageSetting.maxIntervalMinutes.toString(),
                                onValueChange = { value ->
                                    val minutes = value.toIntOrNull()
                                    if (minutes != null && minutes >= settings.proactiveMessageSetting.minIntervalMinutes) {
                                        vm.updateSettings(
                                            settings.copy(
                                                proactiveMessageSetting = settings.proactiveMessageSetting.copy(
                                                    maxIntervalMinutes = minutes
                                                )
                                            )
                                        )
                                    }
                                },
                                placeholder = { Text("90") },
                                singleLine = true,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        },
                    )
                }
            }
            item {
                CardGroup {
                    item(
                        headlineContent = { Text("Forzar apertura de pantalla") },
                        supportingContent = {
                            Text("Permite que la IA use la marca [JUMP] para decidir abrir la pantalla del chat; sólo funciona cuando el usuario supera el umbral de inactividad sin responder")
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.proactiveMessageSetting.allowForceJump,
                                onCheckedChange = { enabled ->
                                    vm.updateSettings(
                                        settings.copy(
                                            proactiveMessageSetting = settings.proactiveMessageSetting.copy(
                                                allowForceJump = enabled
                                            )
                                        )
                                    )
                                }
                            )
                        }
                    )
                    if (settings.proactiveMessageSetting.allowForceJump) {
                        item(
                            headlineContent = { Text("Umbral de inactividad (minutos)") },
                            supportingContent = {
                                OutlinedTextField(
                                    value = settings.proactiveMessageSetting.jumpIdleThresholdMinutes.toString(),
                                    onValueChange = { value ->
                                        val minutes = value.toIntOrNull()
                                        if (minutes != null && minutes > 0) {
                                            vm.updateSettings(
                                                settings.copy(
                                                    proactiveMessageSetting = settings.proactiveMessageSetting.copy(
                                                        jumpIdleThresholdMinutes = minutes
                                                    )
                                                )
                                            )
                                        }
                                    },
                                    placeholder = { Text("120") },
                                    singleLine = true,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            },
                        )
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    val hasExactAlarm = ProactiveMessageWorker.canScheduleExactAlarms(context)
                    CardGroup {
                        item(
                            headlineContent = { Text("Permiso de alarmas exactas") },
                            supportingContent = {
                                if (hasExactAlarm) {
                                    Text("Permiso de alarmas exactas concedido; las activaciones serán más precisas")
                                } else {
                                    Text("No se concedió el permiso de alarmas exactas; la hora puede ser menos precisa. WorkManager se usa automáticamente como respaldo.")
                                }
                            },
                            onClick = if (!hasExactAlarm) {
                                {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                }
                            } else null
                        )
                    }
                }
            }
            item {
                val isIgnoring = ProactiveMessageWorker.isIgnoringBatteryOptimizations(context)
                CardGroup {
                    item(
                        headlineContent = { Text("Optimización de batería") },
                        supportingContent = {
                            if (isIgnoring) {
                                Text("La optimización de batería está ignorada; las activaciones en segundo plano serán más estables")
                            } else {
                                Text("La optimización de batería puede limitar la actividad en segundo plano y retrasar mensajes. Se recomienda excluir la app.")
                            }
                        },
                        onClick = if (!isIgnoring) {
                            {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        } else null
                    )
                }
            }
            item {
                CardGroup {
                    item(
                        headlineContent = { Text("Información") },
                        supportingContent = {
                            Text("Al activarlo, la IA elegirá aleatoriamente un momento entre los intervalos mínimo y máximo para escribirte. Cuando respondas, el temporizador se reinicia; si no respondes, seguirá enviando mensajes por ciclos. La IA puede decidir omitir un mensaje si no tiene nada relevante que decir.\n\nNota: usa AlarmManager + WorkManager para mejorar la puntualidad.")
                        },
                    )
                }
            }
        }
    }
}
