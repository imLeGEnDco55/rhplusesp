package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.memory.CrossWindowMemoryStore
import me.rerere.rikkahub.data.memory.extractFixedMemory
import me.rerere.rikkahub.data.memory.withFixedMemory
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

private enum class MemoryStrategy(
    val label: String,
    val description: String,
    val cost: String,
) {
    NATURAL("Natural · Recomendado", "Conserva una cantidad equilibrada de experiencias recientes y recupera recuerdos antiguos importantes cuando hace falta.", "Memoria media · Tokens medios"),
    SAVER("Ahorrar tokens", "Resume el contenido antiguo de forma más agresiva y conserva menos detalles recientes.", "Menos memoria · Menos tokens"),
    STRONG("Recordar más", "Conserva más detalles recientes y facilita recordar el pasado.", "Más memoria · Más tokens"),
}

@Composable
fun SettingMemoryPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val context = LocalContext.current
    val store = remember { CrossWindowMemoryStore(context) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val assistants = settings.assistants
    val assistant = assistants.firstOrNull { it.id.toString() == selectedId } ?: assistants.firstOrNull()
    var recentRefresh by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun updateAssistant(transform: (Assistant) -> Assistant) {
        val current = assistant ?: return
        vm.updateSettings(settings.copy(assistants = assistants.map { if (it.id == current.id) transform(it) else it }))
    }

    if (confirmClear && assistant != null) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar lo ocurrido recientemente?") },
            text = { Text("Sólo se borrarán los textos recientes y resúmenes organizados de este personaje; no se eliminarán la configuración del personaje, la memoria fija ni la memoria a largo plazo.") },
            confirmButton = {
                TextButton(onClick = {
                    store.clearAssistant(assistant.id.toString())
                    recentRefresh++
                    confirmClear = false
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Memoria") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        if (assistant == null) {
            Column(Modifier.padding(padding).padding(24.dp)) { Text("Crea primero un asistente.") }
            return@Scaffold
        }

        val strategy = detectMemoryStrategy(assistant)
        val assistantId = assistant.id.toString()
        val recent = remember(assistant.id, recentRefresh) { store.peekRecent(assistantId, 30).reversed() }
        val summary = remember(assistant.id, recentRefresh) { store.peekSummary(assistantId) }
        var fixedDraft by remember(assistant.id, assistant.systemPrompt) {
            mutableStateOf(assistant.systemPrompt.extractFixedMemory())
        }
        val rolePrompt = remember(assistant.id, assistant.systemPrompt) {
            assistant.systemPrompt.withFixedMemory("").trim()
        }
        var thresholdDraft by remember(assistant.id, assistant.crossWindowMemoryCompressionThresholdChars) {
            mutableStateOf(assistant.crossWindowMemoryCompressionThresholdChars.toString())
        }
        var tailDraft by remember(assistant.id, assistant.crossWindowMemoryTailEntries) {
            mutableStateOf(assistant.crossWindowMemoryTailEntries.toString())
        }
        var recallDraft by remember(assistant.id, assistant.longTermMemoryRecallCount) {
            mutableStateOf(assistant.longTermMemoryRecallCount.toString())
        }
        var recallCharsDraft by remember(assistant.id, assistant.longTermMemoryMaxChars) {
            mutableStateOf(assistant.longTermMemoryMaxChars.toString())
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Su memoria", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${assistant.name.ifBlank { "TA" }} guardará por capas la información relacionada contigo. Puedes verla, modificarla o eliminarla cuando quieras.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Select(
                    options = assistants,
                    selectedOption = assistant,
                    onOptionSelected = { selectedId = it.id.toString() },
                    optionToString = { it.name.ifBlank { "Asistente sin nombre" } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                CardGroup(title = { Text("🧷 Cosas que no debe olvidar") }) {
                    item(
                        headlineContent = { Text("Configuración del personaje") },
                        supportingContent = {
                            Text(
                                if (rolePrompt.isBlank()) "La configuración del personaje está vacía. Administra la tarjeta desde los ajustes del asistente."
                                else "La tarjeta de personaje se administra desde los ajustes del asistente. Esta página de memoria no la reemplazará."
                            )
                        },
                    )
                }
                if (rolePrompt.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Text(
                            rolePrompt,
                            modifier = Modifier.padding(14.dp),
                            maxLines = 4,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = fixedDraft,
                    onValueChange = { fixedDraft = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    minLines = 4,
                    label = { Text("Memoria fija") },
                    supportingText = { Text("Relaciones, acuerdos, preferencias estables, etc. Los cambios aquí no sobrescriben la tarjeta del personaje.") },
                )
                Button(
                    onClick = {
                        updateAssistant { it.copy(systemPrompt = it.systemPrompt.withFixedMemory(fixedDraft)) }
                    },
                    enabled = fixedDraft.trim() != assistant.systemPrompt.extractFixedMemory(),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Guardar memoria fija") }
            }

            item {
                CardGroup(title = { Text("🌿 Lo que ocurrió recientemente") }) {
                    item(
                        headlineContent = { Text("Memoria reciente") },
                        supportingContent = {
                            Text(
                                when {
                                    !assistant.enableCrossWindowMemory -> "Desactivada. Los distintos chats no compartirán la vida reciente."
                                    summary != null -> "${recent.size}  registros recientes · el contenido anterior ya fue resumido"
                                    recent.isNotEmpty() -> "${recent.size}  registros recientes · todavía no hay contenido antiguo que resumir"
                                    else -> "Aún no hay contenido reciente. Se registrará automáticamente cuando empieces a chatear."
                                }
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = assistant.enableCrossWindowMemory,
                                onCheckedChange = { enabled -> updateAssistant { it.copy(enableCrossWindowMemory = enabled) } },
                            )
                        },
                    )
                }

                summary?.let { savedSummary ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Resumen de contenido anterior", fontWeight = FontWeight.SemiBold)
                            Text(savedSummary.text)
                            Text(
                                "Resumido el ${formatMemoryTime(savedSummary.updatedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (recent.isNotEmpty()) {
                    Text(
                        "Contenido reciente",
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    recent.take(8).forEach { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.text, maxLines = 4)
                                Text(
                                    "${if (entry.role == "user") "Tú" else assistant.name.ifBlank { "TA" }} · ${formatMemoryTime(entry.timestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { recentRefresh++ }) { Text("Actualizar") }
                    TextButton(
                        onClick = { confirmClear = true },
                        enabled = recent.isNotEmpty() || summary != null,
                    ) { Text("Borrar lo ocurrido recientemente") }
                }
            }

            item {
                CardGroup(title = { Text("📚 Cosas de hace mucho tiempo") }) {
                    item(
                        onClick = { nav.navigate(Screen.AssistantMemory(assistantId)) },
                        headlineContent = { Text("Administrar memoria a largo plazo") },
                        supportingContent = { Text("Añade, modifica o elimina recuerdos individualmente. Durante el chat sólo se recuperarán los relacionados con el contenido actual.") },
                    )
                    item(
                        headlineContent = { Text("Activar memoria a largo plazo") },
                        supportingContent = { Text(if (assistant.enableMemory) "Cuando haga falta, buscará contenido relacionado en la memoria a largo plazo." else "Actualmente no recuperará memoria a largo plazo de forma activa.") },
                        trailingContent = {
                            Switch(
                                checked = assistant.enableMemory,
                                onCheckedChange = { enabled -> updateAssistant { it.copy(enableMemory = enabled) } },
                            )
                        },
                    )
                }
            }

            item {
                Text("Modo de memoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Actual: ${strategy?.label ?: "Personalizado"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                MemoryStrategy.entries.forEach { preset ->
                    MemoryPresetCard(
                        strategy = preset,
                        selected = strategy == preset,
                        onClick = { updateAssistant { applyMemoryStrategy(it, preset) } },
                    )
                }
            }

            item {
                TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                    Text(if (advancedExpanded) "Ocultar ajustes avanzados" else "Ajustes avanzados")
                }
                if (advancedExpanded) {
                    CardGroup(title = { Text("Funcionamiento") }) {
                        item(
                            headlineContent = { Text("Memoria de tres capas") },
                            supportingContent = { Text("Memoria fija + flujo de vida reciente + recuperación bajo demanda de memoria a largo plazo") },
                            trailingContent = {
                                Switch(
                                    checked = assistant.enableThreeLayerMemory,
                                    onCheckedChange = { enabled -> updateAssistant { it.copy(enableThreeLayerMemory = enabled) } },
                                )
                            },
                        )
                        item(
                            headlineContent = { Text("Organizar contenido reciente en segundo plano") },
                            supportingContent = { Text("Al superar el umbral, comprime el contenido más antiguo en un resumen.") },
                            trailingContent = {
                                Switch(
                                    checked = assistant.enableCrossWindowMemoryCompression,
                                    onCheckedChange = { enabled -> updateAssistant { it.copy(enableCrossWindowMemoryCompression = enabled) } },
                                )
                            },
                        )
                    }

                    Text("Parámetros personalizados", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                    MemoryNumericField("Caracteres antes de resumir contenido antiguo", thresholdDraft) { thresholdDraft = digitsOnly(it) }
                    MemoryNumericField("Cantidad de registros recientes que se conservarán", tailDraft) { tailDraft = digitsOnly(it) }
                    MemoryNumericField("Máximo de recuerdos a largo plazo por recuperación", recallDraft) { recallDraft = digitsOnly(it) }
                    MemoryNumericField("Máximo de caracteres de memoria a largo plazo por turno", recallCharsDraft) { recallCharsDraft = digitsOnly(it) }
                    Button(
                        onClick = {
                            val threshold = thresholdDraft.toIntOrNull()?.coerceIn(1000, 100000) ?: return@Button
                            val tail = tailDraft.toIntOrNull()?.coerceIn(2, 100) ?: return@Button
                            val recall = recallDraft.toIntOrNull()?.coerceIn(1, 50) ?: return@Button
                            val recallChars = recallCharsDraft.toIntOrNull()?.coerceIn(500, 20000) ?: return@Button
                            updateAssistant {
                                it.copy(
                                    enableThreeLayerMemory = true,
                                    crossWindowMemoryCompressionThresholdChars = threshold,
                                    crossWindowMemoryTailEntries = tail,
                                    longTermMemoryRecallCount = recall,
                                    longTermMemoryMaxChars = recallChars,
                                )
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Guardar parámetros personalizados") }
                }
            }
        }
    }
}

@Composable
private fun MemoryPresetCard(strategy: MemoryStrategy, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(strategy.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(strategy.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(strategy.cost, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemoryNumericField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
        label = { Text(label) },
    )
}

private fun digitsOnly(value: String): String = value.filter(Char::isDigit).take(6)

private fun formatMemoryTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

private fun detectMemoryStrategy(a: Assistant): MemoryStrategy? = when {
    a.crossWindowMemoryCompressionThresholdChars == 8000 && a.crossWindowMemoryTailEntries == 10 && a.longTermMemoryRecallCount == 4 && a.longTermMemoryMaxChars == 1800 -> MemoryStrategy.SAVER
    a.crossWindowMemoryCompressionThresholdChars == 20000 && a.crossWindowMemoryTailEntries == 24 && a.longTermMemoryRecallCount == 10 && a.longTermMemoryMaxChars == 5000 -> MemoryStrategy.STRONG
    a.crossWindowMemoryCompressionThresholdChars == 12000 && a.crossWindowMemoryTailEntries == 16 && a.longTermMemoryRecallCount == 6 && a.longTermMemoryMaxChars == 3000 -> MemoryStrategy.NATURAL
    else -> null
}

private fun applyMemoryStrategy(a: Assistant, strategy: MemoryStrategy): Assistant = when (strategy) {
    MemoryStrategy.NATURAL -> a.copy(
        enableThreeLayerMemory = true,
        enableCrossWindowMemory = true,
        enableCrossWindowMemoryCompression = true,
        crossWindowMemoryCompressionThresholdChars = 12000,
        crossWindowMemoryTailEntries = 16,
        longTermMemoryRecallCount = 6,
        longTermMemoryMaxChars = 3000,
    )
    MemoryStrategy.SAVER -> a.copy(
        enableThreeLayerMemory = true,
        enableCrossWindowMemory = true,
        enableCrossWindowMemoryCompression = true,
        crossWindowMemoryCompressionThresholdChars = 8000,
        crossWindowMemoryTailEntries = 10,
        longTermMemoryRecallCount = 4,
        longTermMemoryMaxChars = 1800,
    )
    MemoryStrategy.STRONG -> a.copy(
        enableThreeLayerMemory = true,
        enableCrossWindowMemory = true,
        enableCrossWindowMemoryCompression = true,
        crossWindowMemoryCompressionThresholdChars = 20000,
        crossWindowMemoryTailEntries = 24,
        longTermMemoryRecallCount = 10,
        longTermMemoryMaxChars = 5000,
    )
}
