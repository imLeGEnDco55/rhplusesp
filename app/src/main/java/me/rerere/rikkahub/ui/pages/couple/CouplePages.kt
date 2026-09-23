package me.rerere.rikkahub.ui.pages.couple

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.CoupleCommentEntity
import me.rerere.rikkahub.data.db.entity.CoupleDiaryEntity
import me.rerere.rikkahub.data.db.entity.CoupleDiaryFolderEntity
import me.rerere.rikkahub.data.db.entity.CouplePostEntity
import me.rerere.rikkahub.data.db.entity.CoupleRelationshipEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import org.json.JSONArray
import org.koin.androidx.compose.koinViewModel

@Composable
fun CoupleSpacePage(vm: CoupleVM = koinViewModel()) {
    val relationship by vm.relationship.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val partner = settings.assistants.firstOrNull { it.id.toString() == relationship?.assistantId }
    var pendingPartnerId by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Espacio de pareja") }, navigationIcon = { BackButton() }) }) { padding ->
        if (relationship == null || partner == null) {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Elige a tu pareja", style = MaterialTheme.typography.headlineSmall) }
                item { Text("Después de vincularla, el espacio compartido, el diario y los aniversarios pertenecerán a los dos.") }
                items(settings.assistants) { assistant ->
                    Card(onClick = { pendingPartnerId = assistant.id.toString() }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(assistant.name.ifBlank { "Asistente sin nombre" }, style = MaterialTheme.typography.titleMedium)
                            Text("Establecer como pareja")
                        }
                    }
                }
            }
        } else {
            CoupleHome(relationship!!, partner.name.ifBlank { "Pareja" }, Modifier.padding(padding))
        }
    }
    pendingPartnerId?.let { assistantId ->
        DateChooserDialog(
            title = "Elegir fecha de inicio de la relación",
            onDismiss = { pendingPartnerId = null },
            onConfirm = { date ->
                vm.bind(assistantId, date)
                pendingPartnerId = null
            },
        )
    }
}

@Composable
private fun CoupleHome(relationship: CoupleRelationshipEntity, partnerName: String, modifier: Modifier = Modifier) {
    val nav = LocalNavController.current
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - relationship.startedAt) + 1
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Yo y $partnerName", style = MaterialTheme.typography.headlineSmall)
                    Text("Día $days de relación", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Desde ${formatDate(relationship.startedAt)}")
                }
            }
        }
        item { FeatureCard("🌙", "Espacio compartido", "Explora las publicaciones, fotos y comentarios tuyos y de $partnerName") { nav.navigate(Screen.CoupleMoments) } }
        item { FeatureCard("📖", "Nuestro diario", "THE PRIVATE JOURNAL · Escribe lo que sientes y espera una respuesta de $partnerName") { nav.navigate(Screen.CoupleDiary) } }
        item { FeatureCard("🎂", "Aniversarios", "Guarda cada día que merezca recordarse") { nav.navigate(Screen.CoupleAnniversaries) } }
    }
}

@Composable
private fun FeatureCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Column { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle) }
        }
    }
}

private enum class SpaceFilter { ALL, USER, AI }

@Composable
fun CoupleMomentsPage(vm: CoupleVM = koinViewModel()) {
    val posts by vm.posts.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val relationship by vm.relationship.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rabbitSpaceUiState by vm.rabbitSpaceUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val partner = settings.assistants.firstOrNull { it.id.toString() == relationship?.assistantId }
    val userName = settings.displaySetting.userNickname.ifBlank { "Yo" }
    val partnerName = partner?.name?.ifBlank { "TA" } ?: "TA"

    var filter by remember { mutableStateOf(SpaceFilter.ALL) }
    var showAdd by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var selectedImageUris by remember { mutableStateOf<List<String>>(emptyList()) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val accepted = uris.take((9 - selectedImageUris.size).coerceAtLeast(0))
        accepted.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        selectedImageUris = (selectedImageUris + accepted.map { it.toString() }).distinct().take(9)
    }

    LaunchedEffect(relationship?.id) {
        if (relationship != null) {
            delay(700)
            vm.maybeCreateAiPost()
        }
    }

    val visiblePosts = when (filter) {
        SpaceFilter.ALL -> posts
        SpaceFilter.USER -> posts.filter { it.author == "user" }
        SpaceFilter.AI -> posts.filter { it.author == "assistant" }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Espacio compartido") }, navigationIcon = { BackButton() }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("＋") } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("🌙 Espacio compartido", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("$userName × $partnerName", style = MaterialTheme.typography.titleMedium)
                        Text("Aquí quedan textos, fotos y comentarios. Ambos pueden publicar y responderse en los comentarios.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { showAdd = true }) { Text("Publicar yo") }
                            OutlinedButton(
                                enabled = !rabbitSpaceUiState.generatingPost,
                                onClick = { vm.maybeCreateAiPost(force = true) },
                            ) {
                                Text(if (rabbitSpaceUiState.generatingPost) "$partnerName está pensando…" else "Pedir a $partnerName que publique")
                            }
                        }
                    }
                }
            }
            if (rabbitSpaceUiState.notice != null || rabbitSpaceUiState.error != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (rabbitSpaceUiState.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                rabbitSpaceUiState.error ?: rabbitSpaceUiState.notice.orEmpty(),
                                modifier = Modifier.weight(1f),
                                color = if (rabbitSpaceUiState.error != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            TextButton(onClick = vm::clearRabbitSpaceFeedback) { Text("Entendido") }
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == SpaceFilter.ALL, onClick = { filter = SpaceFilter.ALL }, label = { Text("Todo") })
                    FilterChip(selected = filter == SpaceFilter.USER, onClick = { filter = SpaceFilter.USER }, label = { Text("$userName — espacio") })
                    FilterChip(selected = filter == SpaceFilter.AI, onClick = { filter = SpaceFilter.AI }, label = { Text("$partnerName — espacio") })
                }
            }
            if (visiblePosts.isEmpty()) {
                item { Text("Aún no hay publicaciones.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(visiblePosts, key = { it.id }) { post ->
                MomentCard(
                    post = post,
                    postComments = comments.filter { it.postId == post.id },
                    userName = userName,
                    partnerName = partnerName,
                    onLike = { vm.toggleLike(post) },
                    onComment = { vm.addUserComment(post, it) },
                    onDelete = { vm.deletePost(post) },
                )
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Nueva publicación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("¿Qué quieres decir ahora?") }, minLines = 3)
                    if (selectedImageUris.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            selectedImageUris.take(3).forEach { uri ->
                                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                            }
                            if (selectedImageUris.size > 3) {
                                Surface(modifier = Modifier.size(68.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                    Box(contentAlignment = Alignment.Center) { Text("+${selectedImageUris.size - 3}") }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(enabled = selectedImageUris.size < 9, onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                            Text(if (selectedImageUris.isEmpty()) "📷 Añadir foto" else "📷 Elegir más fotos")
                        }
                        if (selectedImageUris.isNotEmpty()) TextButton(onClick = { selectedImageUris = emptyList() }) { Text("Borrar todo") }
                    }
                    Text("Máximo 9 fotos · actuales ${selectedImageUris.size}/9", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = draft.isNotBlank() || selectedImageUris.isNotEmpty(), onClick = {
                    vm.addPost(draft.trim(), selectedImageUris)
                    draft = ""
                    selectedImageUris = emptyList()
                    showAdd = false
                }) { Text("Publicar") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MomentCard(
    post: CouplePostEntity,
    postComments: List<CoupleCommentEntity>,
    userName: String,
    partnerName: String,
    onLike: () -> Unit,
    onComment: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val authorName = if (post.author == "assistant") partnerName else userName
    val avatarText = authorName.take(1).ifBlank { if (post.author == "assistant") "A" else "Yo" }
    val images = remember(post.imageUri) { decodePostImages(post.imageUri) }
    var commentDraft by remember(post.id) { mutableStateOf("") }
    var confirmDelete by remember(post.id) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Text(avatarText, fontWeight = FontWeight.Bold) }
                }
                Column {
                    Text(authorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(formatDateTime(post.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (post.content.isNotBlank()) Text(post.content, style = MaterialTheme.typography.bodyLarge)
            if (images.isNotEmpty()) PostImageGrid(images)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { confirmDelete = true }) { Text("Eliminar") }
                TextButton(onClick = onLike) { Text(if (post.liked) "♡ Te gusta" else "♡ Me gusta") }
                TextButton(onClick = { }) { Text("💬 ${postComments.size}") }
            }
            if (postComments.isNotEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        postComments.forEach { comment ->
                            val commentName = if (comment.author == "assistant") partnerName else userName
                            Text(text = "$commentName：${comment.content}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = commentDraft, onValueChange = { commentDraft = it }, modifier = Modifier.weight(1f), placeholder = { Text(if (post.author == "assistant") "Comentar a $partnerName…" else "Escribir comentario…") }, singleLine = true)
                TextButton(enabled = commentDraft.isNotBlank(), onClick = { onComment(commentDraft.trim()); commentDraft = "" }) { Text("Enviar") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar esta publicación?") },
            text = { Text("La publicación y todos sus comentarios se eliminarán. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PostImageGrid(images: List<String>) {
    val rows = images.take(9).chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { rowImages ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowImages.forEach { uri ->
                    AsyncImage(model = uri, contentDescription = "Fotos del espacio", modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                }
                repeat(3 - rowImages.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

private fun decodePostImages(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        if (raw.trimStart().startsWith("[")) {
            val array = JSONArray(raw)
            buildList { for (index in 0 until array.length()) array.optString(index).takeIf { it.isNotBlank() }?.let(::add) }
        } else listOf(raw)
    }.getOrElse { listOf(raw) }
}

private data class JournalPaperPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val background: Color,
    val accent: Color,
    val text: Color,
    val ornament: String,
)

private val journalPaperPresets = listOf(
    JournalPaperPreset("ivory", "Papel marfil", "Papel vintage suave", Color(0xFFFFF9EA), Color(0xFFB58B54), Color(0xFF46392D), "✦"),
    JournalPaperPreset("rose", "Papel rosa", "Carta floral rosa", Color(0xFFFFEFF3), Color(0xFFC66B82), Color(0xFF55363E), "❀"),
    JournalPaperPreset("mist", "Papel azul niebla", "Niebla matinal", Color(0xFFEAF3F8), Color(0xFF678DA5), Color(0xFF314651), "☁"),
    JournalPaperPreset("lavender", "Papel lavanda", "Brisa nocturna", Color(0xFFF3EDFA), Color(0xFF8B70AD), Color(0xFF493D58), "✧"),
    JournalPaperPreset("sage", "Papel salvia", "Cuaderno botánico", Color(0xFFEEF3E8), Color(0xFF718364), Color(0xFF394234), "❧"),
    JournalPaperPreset("night", "Papel de noche lunar", "Luz de luna azul", Color(0xFF20283A), Color(0xFFB8C8EF), Color(0xFFF2F4FA), "☾"),
)

private fun paperPreset(id: String?): JournalPaperPreset = journalPaperPresets.firstOrNull { it.id == id } ?: journalPaperPresets.first()

private data class JournalCoverPreset(
    val id: String,
    val name: String,
    val background: Color,
    val accent: Color,
    val text: Color,
    val ornament: String,
    val subtitle: String,
)

private val journalCoverPresets = listOf(
    JournalCoverPreset("rose_velvet", "Terciopelo rosa", Color(0xFF7D4051), Color(0xFFE8B8C4), Color(0xFFFFF5F7), "❦", "A BOOK OF US"),
    JournalCoverPreset("midnight", "Azul medianoche", Color(0xFF222D49), Color(0xFFB9C8EF), Color(0xFFF5F7FF), "☾", "UNDER THE SAME MOON"),
    JournalCoverPreset("forest", "Verde bosque", Color(0xFF3D564A), Color(0xFFC8D6B9), Color(0xFFF5F7EF), "❧", "OUR QUIET GARDEN"),
    JournalCoverPreset("cream", "Crema clásico", Color(0xFFE9DDC5), Color(0xFF9C7650), Color(0xFF4C3B2C), "✦", "THE PRIVATE JOURNAL"),
    JournalCoverPreset("lavender_cover", "Terciopelo violeta", Color(0xFF554565), Color(0xFFD9C4ED), Color(0xFFFBF7FF), "✧", "LETTERS & MEMORIES"),
)

private fun coverPreset(id: String?): JournalCoverPreset = journalCoverPresets.firstOrNull { it.id == id } ?: journalCoverPresets.first()

private data class ReplyPaperPreset(
    val id: String,
    val name: String,
    val background: Color,
    val accent: Color,
    val text: Color,
    val ornament: String,
)

private val replyPaperPresets = listOf(
    ReplyPaperPreset("cream_letter", "Papel crema", Color(0xFFFFF8E8), Color(0xFFAD8251), Color(0xFF46372B), "✉"),
    ReplyPaperPreset("rose_letter", "Carta de rosas", Color(0xFFFFEDF2), Color(0xFFC66B82), Color(0xFF55363E), "❦"),
    ReplyPaperPreset("airmail", "Carta aérea azul niebla", Color(0xFFEDF6FA), Color(0xFF5E8298), Color(0xFF304650), "⌁"),
    ReplyPaperPreset("moon_letter", "Azul lunar profundo", Color(0xFF252D43), Color(0xFFBECAF0), Color(0xFFF4F6FF), "☾"),
)

private fun replyPaperPreset(id: String?): ReplyPaperPreset = replyPaperPresets.firstOrNull { it.id == id } ?: replyPaperPresets.first()

private fun Modifier.journalPaperTexture(paper: JournalPaperPreset): Modifier = drawBehind {
    val faint = paper.accent.copy(alpha = if (paper.id == "night") 0.12f else 0.09f)
    when (paper.id) {
        "ivory", "rose", "sage" -> {
            var y = 36f
            while (y < size.height) {
                drawLine(faint, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += 34f
            }
            if (paper.id == "sage") {
                var x = 28f
                while (x < size.width) {
                    drawCircle(faint, radius = 2.2f, center = Offset(x, 20f + (x % 48f)))
                    x += 44f
                }
            }
        }
        "mist" -> {
            var y = 22f
            while (y < size.height) {
                var x = 18f
                while (x < size.width) {
                    drawCircle(faint, radius = 1.5f, center = Offset(x, y))
                    x += 26f
                }
                y += 26f
            }
        }
        "lavender" -> {
            var y = 26f
            while (y < size.height) {
                var x = 24f
                while (x < size.width) {
                    drawLine(faint, Offset(x - 3f, y), Offset(x + 3f, y), strokeWidth = 1f)
                    drawLine(faint, Offset(x, y - 3f), Offset(x, y + 3f), strokeWidth = 1f)
                    x += 42f
                }
                y += 42f
            }
        }
        "night" -> {
            var y = 24f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 22f else 42f
                while (x < size.width) {
                    drawCircle(faint, radius = if (((x + y).toInt() / 20) % 3 == 0) 2.2f else 1.2f, center = Offset(x, y))
                    x += 54f
                }
                row++
                y += 42f
            }
        }
    }
}

private fun Modifier.replyPaperTexture(paper: ReplyPaperPreset): Modifier = drawBehind {
    val faint = paper.accent.copy(alpha = if (paper.id == "moon_letter") 0.13f else 0.10f)
    if (paper.id == "airmail") {
        var y = 34f
        while (y < size.height) {
            drawLine(faint, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += 32f
        }
        drawLine(paper.accent.copy(alpha = 0.24f), Offset(20f, 0f), Offset(20f, size.height), strokeWidth = 2f)
    } else {
        var y = 26f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 24f else 44f
            while (x < size.width) {
                drawCircle(faint, radius = if (paper.id == "moon_letter") 1.7f else 1.2f, center = Offset(x, y))
                x += 52f
            }
            row++
            y += 44f
        }
    }
}

private enum class JournalViewMode { PAGES, TIMELINE, BOOKMARKS }

@Composable
fun CoupleDiaryPage(vm: CoupleVM = koinViewModel()) {
    val entries by vm.diaries.collectAsStateWithLifecycle()
    val persistedFolders by vm.diaryFolders.collectAsStateWithLifecycle()
    val relationship by vm.relationship.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val partner = settings.assistants.firstOrNull { it.id.toString() == relationship?.assistantId }
    val userName = settings.displaySetting.userNickname.ifBlank { "Yo" }
    val partnerName = partner?.name?.ifBlank { "TA" } ?: "TA"
    val cover = coverPreset(relationship?.journalCover)

    var query by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf("Todas las entradas") }
    var viewMode by remember { mutableStateOf(JournalViewMode.PAGES) }
    var showAdd by remember { mutableStateOf(false) }
    var showFolderManager by remember { mutableStateOf(false) }
    var showCoverPicker by remember { mutableStateOf(false) }
    var showInscriptionEditor by remember { mutableStateOf(false) }
    var selectedDiaryId by remember { mutableStateOf<String?>(null) }
    var editingDiaryId by remember { mutableStateOf<String?>(null) }
    var requestedReplyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entries, persistedFolders) {
        val existing = persistedFolders.map { it.name.lowercase() }.toSet()
        entries.mapNotNull { it.folder?.trim()?.takeIf { name -> name.isNotBlank() && name != "Todas las entradas" } }
            .distinct()
            .filter { it.lowercase() !in existing }
            .forEach(vm::addDiaryFolder)
    }

    val folders = remember(entries, persistedFolders) {
        listOf("Todas las entradas") + (persistedFolders.map { it.name } + entries.mapNotNull { it.folder })
            .filter { it.isNotBlank() && it != "Todas las entradas" }
            .distinct()
    }
    if (selectedFolder !in folders) selectedFolder = "Todas las entradas"

    val filteredEntries = entries.filter { entry ->
        val folderMatch = selectedFolder == "Todas las entradas" || entry.folder == selectedFolder
        val queryMatch = query.isBlank() || entry.title.contains(query, true) || entry.content.contains(query, true)
        val modeMatch = viewMode != JournalViewMode.BOOKMARKS || entry.bookmarked
        folderMatch && queryMatch && modeMatch
    }
    val timelineGroups = remember(filteredEntries) {
        val formatter = SimpleDateFormat("yyyy年MM月", Locale.getDefault())
        filteredEntries.groupBy { formatter.format(Date(it.entryDate)) }
    }
    val bookmarkedCount = entries.count { it.bookmarked }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nuestro diario") }, navigationIcon = { BackButton() }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("＋") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                JournalCoverCard(
                    cover = cover,
                    coverTitle = relationship?.journalCoverTitle ?: "Nuestro diario",
                    coverDate = relationship?.journalCoverDate ?: "SINCE ${formatDate(relationship?.startedAt ?: System.currentTimeMillis())}",
                    userName = userName,
                    partnerName = partnerName,
                    entryCount = entries.size,
                    bookmarkedCount = bookmarkedCount,
                    onChangeCover = { showCoverPicker = true },
                    onEditInscription = { showInscriptionEditor = true },
                )
            }
            item {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp), placeholder = { Text("Buscar entradas…") }, singleLine = true)
            }
            item {
                LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = viewMode == JournalViewMode.PAGES, onClick = { viewMode = JournalViewMode.PAGES }, label = { Text("Todas las páginas") }) }
                    item { FilterChip(selected = viewMode == JournalViewMode.TIMELINE, onClick = { viewMode = JournalViewMode.TIMELINE }, label = { Text("Cronología") }) }
                    item { FilterChip(selected = viewMode == JournalViewMode.BOOKMARKS, onClick = { viewMode = JournalViewMode.BOOKMARKS }, label = { Text("🔖 Marcadores $bookmarkedCount") }) }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(folders) { folder ->
                            FilterChip(selected = selectedFolder == folder, onClick = { selectedFolder = folder }, label = { Text(folder) })
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showFolderManager = true }) { Text("🗂 Administrar categorías") }
                    }
                }
            }
            if (filteredEntries.isEmpty()) {
                item {
                    Text(
                        when {
                            viewMode == JournalViewMode.BOOKMARKS -> "Aún no hay páginas guardadas. Cuando quieras conservar una especialmente, añádele un marcador."
                            query.isNotBlank() -> "No se encontró esta entrada."
                            else -> "Aún no has escrito nada aquí."
                        },
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (viewMode == JournalViewMode.TIMELINE) {
                timelineGroups.forEach { (month, monthEntries) ->
                    item(key = "month-$month") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(Modifier.size(12.dp))
                            }
                            Text(month, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                    }
                    items(monthEntries, key = { "timeline-${it.id}" }) { entry ->
                        JournalCard(
                            entry = entry,
                            partnerName = partnerName,
                            onClick = { selectedDiaryId = entry.id },
                            onBookmark = { vm.toggleDiaryBookmark(entry) },
                            timeline = true,
                        )
                    }
                }
            } else {
                items(filteredEntries, key = { it.id }) { entry ->
                    JournalCard(
                        entry = entry,
                        partnerName = partnerName,
                        onClick = { selectedDiaryId = entry.id },
                        onBookmark = { vm.toggleDiaryBookmark(entry) },
                    )
                }
            }
        }
    }

    if (showCoverPicker) {
        JournalCoverPickerDialog(
            selected = relationship?.journalCover ?: "rose_velvet",
            onDismiss = { showCoverPicker = false },
            onSelect = {
                vm.setJournalCover(it)
                showCoverPicker = false
            },
        )
    }

    if (showInscriptionEditor) {
        JournalInscriptionDialog(
            initialTitle = relationship?.journalCoverTitle ?: "Nuestro diario",
            initialDate = relationship?.journalCoverDate ?: "SINCE ${formatDate(relationship?.startedAt ?: System.currentTimeMillis())}",
            onDismiss = { showInscriptionEditor = false },
            onSave = { title, date ->
                vm.setJournalInscription(title, date)
                showInscriptionEditor = false
            },
        )
    }

    if (showAdd) {
        JournalEditorDialog(
            titleText = "Escribir una entrada",
            initial = null,
            existingFolders = folders,
            onDismiss = { showAdd = false },
            onSave = { title, content, folder, paper ->
                if (folder != "Todas las entradas" && folders.none { it.equals(folder, true) }) vm.addDiaryFolder(folder)
                vm.addDiary(title, content, folder, paper)
                showAdd = false
            },
        )
    }

    if (showFolderManager) {
        JournalFolderManagerDialog(
            folders = persistedFolders,
            onDismiss = { showFolderManager = false },
            onAdd = vm::addDiaryFolder,
            onRename = vm::renameDiaryFolder,
            onDelete = { folder ->
                if (selectedFolder == folder.name) selectedFolder = "Todas las entradas"
                vm.deleteDiaryFolder(folder)
            },
        )
    }

    editingDiaryId?.let { diaryId ->
        entries.firstOrNull { it.id == diaryId }?.let { entry ->
            JournalEditorDialog(
                titleText = "Editar esta entrada",
                initial = entry,
                existingFolders = folders,
                onDismiss = { editingDiaryId = null },
                onSave = { title, content, folder, paper ->
                    if (folder != "Todas las entradas" && folders.none { it.equals(folder, true) }) vm.addDiaryFolder(folder)
                    vm.updateDiary(entry, title, content, folder, paper)
                    editingDiaryId = null
                    selectedDiaryId = entry.id
                },
            )
        }
    }

    selectedDiaryId?.let { diaryId ->
        entries.firstOrNull { it.id == diaryId }?.let { current ->
            LaunchedEffect(current.reply) { if (!current.reply.isNullOrBlank()) requestedReplyId = null }
            JournalReaderDialog(
                entry = current,
                partnerName = partnerName,
                waitingForReply = requestedReplyId == current.id && current.reply.isNullOrBlank(),
                onDismiss = { selectedDiaryId = null },
                onEdit = {
                    selectedDiaryId = null
                    editingDiaryId = current.id
                },
                onBookmark = { vm.toggleDiaryBookmark(current) },
                onRequestReply = {
                    requestedReplyId = current.id
                    vm.requestDiaryReply(current)
                },
                onReplyPaperChange = { vm.setDiaryReplyPaper(current, it) },
            )
        }
    }
}

@Composable
private fun JournalCoverCard(
    cover: JournalCoverPreset,
    coverTitle: String,
    coverDate: String,
    userName: String,
    partnerName: String,
    entryCount: Int,
    bookmarkedCount: Int,
    onChangeCover: () -> Unit,
    onEditInscription: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(26.dp),
        color = cover.background,
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, cover.accent.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(24.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(cover.ornament, style = MaterialTheme.typography.headlineMedium, color = cover.accent)
                Row {
                    TextButton(onClick = onEditInscription, colors = ButtonDefaults.textButtonColors(contentColor = cover.accent)) { Text("Texto dorado") }
                    TextButton(onClick = onChangeCover, colors = ButtonDefaults.textButtonColors(contentColor = cover.accent)) { Text("Cambiar portada") }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(coverTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = cover.text)
                Text("THE PRIVATE JOURNAL", style = MaterialTheme.typography.labelLarge, color = cover.accent)
                Text(cover.subtitle, style = MaterialTheme.typography.labelMedium, color = cover.text.copy(alpha = 0.62f))
                Text(coverDate, style = MaterialTheme.typography.labelSmall, color = cover.accent)
            }
            HorizontalDivider(color = cover.accent.copy(alpha = 0.3f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$userName × $partnerName", color = cover.text, style = MaterialTheme.typography.titleMedium)
                Column(horizontalAlignment = Alignment.End) {
                    Text("$entryCount entradas", color = cover.accent, style = MaterialTheme.typography.labelLarge)
                    if (bookmarkedCount > 0) Text("🔖 $bookmarkedCount favoritas", color = cover.text.copy(alpha = 0.68f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun JournalInscriptionDialog(
    initialTitle: String,
    initialDate: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var date by remember(initialDate) { mutableStateOf(initialDate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personalizar texto dorado de portada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Estas dos líneas aparecerán directamente en la portada. Puedes poner nombres, una frase o fecha; déjalo vacío para volver al valor predeterminado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = title, onValueChange = { title = it.take(28) }, modifier = Modifier.fillMaxWidth(), label = { Text("Título de portada") }, singleLine = true)
                OutlinedTextField(value = date, onValueChange = { date = it.take(36) }, modifier = Modifier.fillMaxWidth(), label = { Text("Fecha / texto pequeño") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, date) }) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun JournalCoverPickerDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir portada del diario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("La portada se conservará hasta que la cambies de nuevo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                journalCoverPresets.forEach { cover ->
                    Surface(
                        onClick = { onSelect(cover.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = cover.background,
                        border = BorderStroke(if (selected == cover.id) 2.dp else 1.dp, if (selected == cover.id) cover.accent else cover.accent.copy(alpha = 0.28f)),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(cover.ornament, color = cover.accent, style = MaterialTheme.typography.titleLarge)
                            Column(Modifier.weight(1f)) {
                                Text(cover.name, color = cover.text, fontWeight = FontWeight.SemiBold)
                                Text(cover.subtitle, color = cover.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
                            }
                            if (selected == cover.id) Text("✓", color = cover.accent)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun JournalCard(
    entry: CoupleDiaryEntity,
    partnerName: String,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    timeline: Boolean = false,
) {
    val paper = paperPreset(entry.paper)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = if (timeline) 18.dp else 0.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (timeline) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
                Surface(shape = CircleShape, color = paper.accent) { Box(Modifier.size(9.dp)) }
                Box(Modifier.width(1.dp).height(118.dp).drawBehind { drawRect(paper.accent.copy(alpha = 0.24f)) })
            }
        }
        Card(
            onClick = onClick,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            colors = CardDefaults.cardColors(containerColor = paper.background),
        ) {
            Column(Modifier.fillMaxWidth().journalPaperTexture(paper).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = paper.text)
                    TextButton(onClick = onBookmark, colors = ButtonDefaults.textButtonColors(contentColor = paper.accent)) {
                        Text(if (entry.bookmarked) "🔖" else "♡")
                    }
                }
                Text(formatDate(entry.entryDate), style = MaterialTheme.typography.bodySmall, color = paper.text.copy(alpha = 0.68f))
                Text(entry.content.replace("\n", " ").take(120) + if (entry.content.length > 120) "…" else "", style = MaterialTheme.typography.bodyMedium, color = paper.text.copy(alpha = 0.78f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${paper.ornament} ${entry.folder ?: "Todas las entradas"}", style = MaterialTheme.typography.labelMedium, color = paper.accent)
                    if (entry.bookmarked) Text("Páginas favoritas", style = MaterialTheme.typography.labelMedium, color = paper.accent)
                    if (!entry.reply.isNullOrBlank()) Text("✉ Respuesta recibida de $partnerName", style = MaterialTheme.typography.labelMedium, color = paper.accent)
                    else Text("Aún no hay respuesta", style = MaterialTheme.typography.labelMedium, color = paper.text.copy(alpha = 0.58f))
                }
            }
        }
    }
}

@Composable
private fun JournalEditorDialog(
    titleText: String,
    initial: CoupleDiaryEntity?,
    existingFolders: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    var folder by remember(initial?.id) { mutableStateOf(initial?.folder ?: "Todas las entradas") }
    var paper by remember(initial?.id) { mutableStateOf(initial?.paper ?: "ivory") }
    var customFolder by remember(initial?.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Título") }, singleLine = true)
                OutlinedTextField(content, { content = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Texto") }, minLines = 7)
                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    existingFolders.forEach { name ->
                        FilterChip(selected = folder == name, onClick = { folder = name; customFolder = "" }, label = { Text(name) })
                    }
                }
                OutlinedTextField(value = customFolder, onValueChange = { customFolder = it; if (it.isNotBlank()) folder = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("Nueva categoría (opcional)") }, singleLine = true)
                Text("Papel del diario", style = MaterialTheme.typography.labelLarge)
                PaperSelector(selected = paper, onSelect = { paper = it })
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && content.isNotBlank(), onClick = { onSave(title.trim(), content.trim(), folder.ifBlank { "Todas las entradas" }, paper) }) {
                Text(if (initial == null) "Guardar página" else "Guardar cambios")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PaperSelector(selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(journalPaperPresets, key = { it.id }) { paper ->
            Surface(
                onClick = { onSelect(paper.id) },
                modifier = Modifier.width(112.dp).height(90.dp),
                shape = RoundedCornerShape(14.dp),
                color = paper.background,
                border = BorderStroke(if (selected == paper.id) 2.dp else 1.dp, if (selected == paper.id) paper.accent else paper.accent.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.fillMaxSize().journalPaperTexture(paper).padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(paper.ornament, color = paper.accent, style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text(paper.name, color = paper.text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(paper.subtitle, color = paper.text.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalFolderManagerDialog(
    folders: List<CoupleDiaryFolderEntity>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (CoupleDiaryFolderEntity, String) -> Unit,
    onDelete: (CoupleDiaryFolderEntity) -> Unit,
) {
    var newFolder by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Administrar categorías del diario") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("“Todas las entradas” es la categoría predeterminada y no se puede eliminar. Si eliminas otra categoría, sus entradas volverán automáticamente a “Todas las entradas”.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newFolder, { newFolder = it }, modifier = Modifier.weight(1f), label = { Text("Nueva categoría") }, singleLine = true)
                    FilledTonalButton(enabled = newFolder.isNotBlank(), onClick = { onAdd(newFolder); newFolder = "" }) { Text("Crear") }
                }
                HorizontalDivider()
                if (folders.isEmpty()) Text("Aún no hay categorías personalizadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                folders.forEach { folder ->
                    if (editingId == folder.id) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(editingName, { editingName = it }, modifier = Modifier.weight(1f), singleLine = true)
                            TextButton(enabled = editingName.isNotBlank(), onClick = { onRename(folder, editingName); editingId = null; editingName = "" }) { Text("Guardar") }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(folder.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { editingId = folder.id; editingName = folder.name }) { Text("Renombrar") }
                            TextButton(onClick = { onDelete(folder) }) { Text("Eliminar") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

@Composable
private fun JournalReaderDialog(
    entry: CoupleDiaryEntity,
    partnerName: String,
    waitingForReply: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onBookmark: () -> Unit,
    onRequestReply: () -> Unit,
    onReplyPaperChange: (String) -> Unit,
) {
    val paper = paperPreset(entry.paper)
    var page by remember(entry.id) { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (page == 0) paper.background else replyPaperPreset(entry.replyPaper).background,
            border = BorderStroke(1.dp, if (page == 0) paper.accent.copy(alpha = 0.28f) else replyPaperPreset(entry.replyPaper).accent.copy(alpha = 0.28f)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp, 10.dp, 14.dp, 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = page == 0, onClick = { page = 0 }, label = { Text("01 Diario") })
                        FilterChip(selected = page == 1, onClick = { page = 1 }, label = { Text("02 Respuesta") })
                    }
                    Text("${page + 1} / 2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                        } else {
                            slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                        }
                    },
                    label = "journal-page-turn",
                ) { currentPage ->
                    if (currentPage == 0) {
                        Column(
                            Modifier.fillMaxWidth().journalPaperTexture(paper).padding(22.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("THE PRIVATE JOURNAL", style = MaterialTheme.typography.labelMedium, color = paper.accent)
                                TextButton(onClick = onBookmark, colors = ButtonDefaults.textButtonColors(contentColor = paper.accent)) {
                                    Text(if (entry.bookmarked) "🔖 Guardado" else "♡ Añadir marcador")
                                }
                            }
                            Text(entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = paper.text)
                            Text("${formatDate(entry.entryDate)} · ${entry.folder ?: "Todas las entradas"} · ${paper.name}", style = MaterialTheme.typography.bodySmall, color = paper.text.copy(alpha = 0.62f))
                            HorizontalDivider(color = paper.accent.copy(alpha = 0.3f))
                            Text(entry.content, style = MaterialTheme.typography.bodyLarge, color = paper.text)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = onEdit) { Text("Editar página", color = paper.accent) }
                                FilledTonalButton(onClick = { page = 1 }) { Text("Ir a la respuesta →") }
                            }
                        }
                    } else {
                        val replyPaper = replyPaperPreset(entry.replyPaper)
                        Column(
                            Modifier.fillMaxWidth().replyPaperTexture(replyPaper).padding(22.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("A LETTER FROM $partnerName", style = MaterialTheme.typography.labelMedium, color = replyPaper.accent)
                                    Text("Respuesta a esta página", style = MaterialTheme.typography.bodySmall, color = replyPaper.text.copy(alpha = 0.62f))
                                }
                                Text(replyPaper.ornament, style = MaterialTheme.typography.titleLarge, color = replyPaper.accent)
                            }
                            HorizontalDivider(color = replyPaper.accent.copy(alpha = 0.28f))
                            when {
                                !entry.reply.isNullOrBlank() -> {
                                    Text(entry.reply, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = replyPaper.text)
                                    entry.replyAt?.let { Text("— $partnerName · ${formatDateTime(it)}", style = MaterialTheme.typography.bodySmall, color = replyPaper.text.copy(alpha = 0.62f)) }
                                    Text("Papel de respuesta", style = MaterialTheme.typography.labelLarge, color = replyPaper.text)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(replyPaperPresets, key = { it.id }) { preset ->
                                            FilterChip(
                                                selected = replyPaper.id == preset.id,
                                                onClick = { onReplyPaperChange(preset.id) },
                                                label = { Text(preset.name) },
                                            )
                                        }
                                    }
                                }
                                waitingForReply -> Text("La carta ya fue enviada; esperando a que $partnerName responda…", color = replyPaper.accent)
                                else -> Text("Esta página aún no tiene respuesta. Entrégasela a $partnerName y espera una carta escrita sólo para ti.", color = replyPaper.text.copy(alpha = 0.72f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { page = 0 }) { Text("← Volver al diario", color = replyPaper.accent) }
                                FilledTonalButton(enabled = !waitingForReply, onClick = onRequestReply) {
                                    Text(if (entry.reply.isNullOrBlank()) "Pedir a $partnerName que responda" else "Pedir a $partnerName otra respuesta")
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cerrar diario") }
                }
            }
        }
    }
}

@Composable
fun CoupleAnniversariesPage(vm: CoupleVM = koinViewModel()) {
    val entries by vm.anniversaries.collectAsStateWithLifecycle()
    EntryListPage("Aniversarios", "Añadir aniversario", entries, { it.title }, { formatDate(it.eventDate) }, chooseDate = true) { title, _, date -> vm.addAnniversary(title, date) }
}

@Composable
private fun <T> EntryListPage(
    title: String,
    action: String,
    entries: List<T>,
    content: (T) -> String,
    date: (T) -> String,
    onLike: ((T) -> Unit)? = null,
    likeLabel: ((T) -> String)? = null,
    chooseDate: Boolean = false,
    onAdd: (String, String, Long) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDateChooser by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton() }) }, floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("＋") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (entries.isEmpty()) item { Text("Aún no hay contenido. Toca abajo a la derecha para empezar.") }
            items(entries) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(content(entry), style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(date(entry), style = MaterialTheme.typography.bodySmall)
                            if (onLike != null) TextButton(onClick = { onLike(entry) }) { Text(likeLabel?.invoke(entry) ?: "Me gusta") }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text(action) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text("Título") })
                if (title == "Diario") OutlinedTextField(second, { second = it }, label = { Text("Texto") }, minLines = 4)
                if (chooseDate) TextButton(onClick = { showDateChooser = true }) { Text("Fecha: ${formatDate(selectedDate)}") }
            }
        },
        confirmButton = { TextButton(enabled = first.isNotBlank(), onClick = { onAdd(first, second, selectedDate); first = ""; second = ""; showAdd = false }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } },
    )
    if (showDateChooser) DateChooserDialog(title = "Elegir aniversario", initialDate = selectedDate, onDismiss = { showDateChooser = false }, onConfirm = { selectedDate = it; showDateChooser = false })
}

@Composable
private fun DateChooserDialog(
    title: String,
    initialDate: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate)
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { onConfirm(state.selectedDateMillis ?: initialDate) }) { Text("Aceptar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp, 20.dp, 24.dp, 0.dp))
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

private fun formatDate(value: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(value))
private fun formatDateTime(value: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
