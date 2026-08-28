package com.aigameguide.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aigameguide.app.R
import com.aigameguide.app.data.db.GameEntity
import com.aigameguide.app.data.db.GuideQuestionEntity
import com.aigameguide.app.data.model.MessageRole
import com.aigameguide.app.data.model.Platform
import com.aigameguide.app.data.model.PlayStyle
import com.aigameguide.app.data.model.SpoilerLevel
import com.aigameguide.app.ui.theme.GuideBlue
import com.aigameguide.app.ui.theme.GuidePink
import com.aigameguide.app.ui.theme.GuidePurple
import com.aigameguide.app.ui.theme.SoftBorder
import com.aigameguide.app.viewmodel.GuideViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameGuideRoot(vm: GuideViewModel) {
    val games by vm.games.collectAsState()
    val selected by vm.selectedGame.collectAsState()
    val messages by vm.messages.collectAsState()
    val composer by vm.composer.collectAsState()
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var settingsDialog by rememberSaveable { mutableStateOf(false) }
    var progressDialog by remember { mutableStateOf<GameEntity?>(null) }
    var phoneDetail by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.app_icon),
                            contentDescription = null,
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("AI 게임 공략 비서", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { settingsDialog = true }) {
                        Icon(Icons.Rounded.Settings, "AI 설정")
                    }
                    IconButton(onClick = { }) { Icon(Icons.Rounded.History, "최근 기록") }
                    IconButton(onClick = { settingsDialog = true }) { Icon(Icons.Rounded.MoreVert, "더보기") }
                }
            )
        }
    ) { inner ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(inner)) {
            val twoPane = maxWidth >= 720.dp
            if (twoPane) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Surface(
                        modifier = Modifier.weight(0.39f).fillMaxHeight(),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, SoftBorder),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        GameListPane(games, selected?.id, onAdd = { addDialog = true }, onSelect = vm::selectGame,
                            onQuestion = vm::selectGame, onProgress = { progressDialog = it })
                    }
                    Spacer(Modifier.width(14.dp))
                    Surface(
                        modifier = Modifier.weight(0.61f).fillMaxHeight(),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, SoftBorder),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        ChatPane(selected, messages, composer.imagePaths, composer.isSending, composer.error,
                            composer.webSearch, vm, onBack = null, onEdit = { progressDialog = it })
                    }
                }
            } else if (phoneDetail && selected != null) {
                ChatPane(selected, messages, composer.imagePaths, composer.isSending, composer.error,
                    composer.webSearch, vm, onBack = { phoneDetail = false }, onEdit = { progressDialog = it },
                    modifier = Modifier.fillMaxSize())
            } else {
                GameListPane(games, selected?.id, onAdd = { addDialog = true },
                    onSelect = { vm.selectGame(it); phoneDetail = true },
                    onQuestion = { vm.selectGame(it); phoneDetail = true },
                    onProgress = { progressDialog = it }, modifier = Modifier.fillMaxSize())
            }
        }
    }

    if (addDialog) AddGameDialog(onDismiss = { addDialog = false }) {
        vm.addGame(it); addDialog = false; phoneDetail = true
    }
    progressDialog?.let { game ->
        ProgressDialog(game, onDismiss = { progressDialog = null }, onSave = {
            vm.updateGame(it); progressDialog = null
        }, onDelete = {
            vm.deleteGame(game.id); progressDialog = null; phoneDetail = false
        })
    }
    if (settingsDialog) AiSettingsDialog(vm, onDismiss = { settingsDialog = false })
}

@Composable
private fun GameListPane(
    games: List<GameEntity>, selectedId: Long?, onAdd: () -> Unit, onSelect: (Long) -> Unit,
    onQuestion: (Long) -> Unit, onProgress: (GameEntity) -> Unit, modifier: Modifier = Modifier
) {
    var search by rememberSaveable { mutableStateOf("") }
    Column(modifier.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("플레이 중인 게임", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text("등록")
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("게임 검색") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true, shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(12.dp))
        val filtered = games.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
        if (filtered.isEmpty()) {
            EmptyGames(onAdd, Modifier.weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { game ->
                    GameCard(game, game.id == selectedId, { onSelect(game.id) }, { onQuestion(game.id) }, { onProgress(game) })
                }
            }
        }
    }
}

@Composable
private fun EmptyGames(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(94.dp).clip(CircleShape).background(
                Brush.linearGradient(listOf(Color(0xFFE6F7FF), Color(0xFFECE4FF)))
            ), contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AutoAwesome, null, tint = GuidePurple, modifier = Modifier.size(46.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("플레이 중인 게임을 등록하세요", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text("진행도를 기억하고 스크린샷으로 공략해 드려요", color = Color.Gray, modifier = Modifier.padding(top = 7.dp))
        Button(onClick = onAdd, modifier = Modifier.padding(top = 20.dp).height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("첫 게임 등록")
        }
    }
}

@Composable
private fun GameCard(game: GameEntity, selected: Boolean, onClick: () -> Unit, onQuestion: () -> Unit, onProgress: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFF6F5FF) else Color.White),
        border = BorderStroke(if (selected) 1.7.dp else 1.dp, if (selected) GuideBlue else SoftBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                GameCover(game)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(game.platform, color = Color(0xFF777483), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(9.dp))
                    Row { Text("진행률 ${game.progressPercent}%", fontSize = 14.sp); Spacer(Modifier.weight(1f)); Text("${game.playHours.toInt()}시간", fontSize = 14.sp) }
                    LinearProgressIndicator(
                        progress = { game.progressPercent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(CircleShape),
                        color = GuideBlue, trackColor = Color(0xFFE7E7EF)
                    )
                    Text("최근 진행: ${game.mainQuest.ifBlank { game.chapter.ifBlank { "아직 입력되지 않음" } }}",
                        color = GuidePurple, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 9.dp))
                }
            }
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(Icons.Rounded.AutoAwesome, "공략 질문", onQuestion, Modifier.weight(1f))
                SmallAction(Icons.Rounded.Image, "스크린샷", onQuestion, Modifier.weight(1f))
                SmallAction(Icons.Rounded.BarChart, "진행도", onProgress, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GameCover(game: GameEntity) {
    Box(
        Modifier.size(92.dp).clip(RoundedCornerShape(17.dp)).background(
            Brush.linearGradient(listOf(GuideBlue, GuidePurple, GuidePink))
        ), contentAlignment = Alignment.Center
    ) {
        if (game.coverUri != null) AsyncImage(game.coverUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(game.name.take(1), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, click: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = click, modifier = modifier.height(48.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(4.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ChatPane(
    game: GameEntity?, messages: List<GuideQuestionEntity>, imagePaths: List<String>, sending: Boolean,
    error: String?, webSearch: Boolean, vm: GuideViewModel, onBack: (() -> Unit)?, onEdit: (GameEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (game == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("왼쪽에서 게임을 선택하세요", color = Color.Gray, fontSize = 18.sp)
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로") }
            GameCoverSmall(game)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(game.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SpoilerBadge(game.spoilerLevel)
            }
            IconButton(onClick = { onEdit(game) }) { Icon(Icons.Rounded.Edit, "진행도 편집") }
        }
        HorizontalDivider(color = SoftBorder)
        if (messages.isEmpty()) WelcomeGuide(game, Modifier.weight(1f))
        else LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { items(messages, key = { it.id }) { MessageBubble(it) } }
        error?.let { ErrorBar(it, vm::clearError) }
        GuideComposer(game, imagePaths, sending, webSearch, vm)
    }
}

@Composable
private fun GameCoverSmall(game: GameEntity) {
    Box(Modifier.size(49.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(GuideBlue, GuidePurple))), contentAlignment = Alignment.Center) {
        if (game.coverUri != null) AsyncImage(game.coverUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(game.name.take(1), color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
    }
}

@Composable
private fun SpoilerBadge(value: String) {
    val level = runCatching { SpoilerLevel.valueOf(value) }.getOrDefault(SpoilerLevel.NONE)
    Surface(color = Color(0xFFF0ECFF), shape = RoundedCornerShape(9.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = GuidePurple, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp))
            Text(level.label, color = GuidePurple, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WelcomeGuide(game: GameEntity, modifier: Modifier) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = GuidePurple, modifier = Modifier.size(48.dp))
        Text("${game.name} 공략을 시작해 보세요", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text("스크린샷을 최대 5장 첨부하고\n“여기서 어디 가야 돼?”라고 물어보면 됩니다.",
            color = Color.Gray, lineHeight = 23.sp, modifier = Modifier.padding(top = 8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5FF)),
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(top = 22.dp)
        ) {
            Column(Modifier.padding(17.dp)) {
                Text("현재 저장된 진행도", color = GuidePurple, fontWeight = FontWeight.Bold)
                Text("${game.progressPercent}% · ${game.playHours.toInt()}시간", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
                Text(game.mainQuest.ifBlank { game.chapter.ifBlank { "진행도를 입력하면 답변 정확도가 올라갑니다." } }, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(message: GuideQuestionEntity) {
    val user = message.role == MessageRole.USER.name
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val canExpand = !user && message.content.length > 520
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        if (!user) {
            Box(Modifier.size(31.dp).clip(CircleShape).background(Brush.linearGradient(listOf(GuideBlue, GuidePurple))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Card(
            modifier = Modifier.fillMaxWidth(if (user) 0.80f else 0.92f),
            colors = CardDefaults.cardColors(containerColor = if (user) Color(0xFFE8EAFF) else Color.White),
            shape = RoundedCornerShape(20.dp),
            border = if (user) null else BorderStroke(1.dp, SoftBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = if (user) 0.dp else 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                if (!user) Text("✦ 지금 할 것", color = GuidePurple, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    message.content,
                    lineHeight = 22.sp,
                    maxLines = if (canExpand && !expanded) 8 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (user) 0.dp else 8.dp)
                )
                if (canExpand) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (expanded) "접기" else "자세히 보기")
                }
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.End) {
                    if (message.usedWeb) {
                        Icon(Icons.Rounded.Language, null, tint = GuideBlue, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(3.dp)); Text("웹 검증", color = GuideBlue, fontSize = 11.sp); Spacer(Modifier.width(8.dp))
                    }
                    Text(SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(message.createdAt)), color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ErrorBar(error: String, dismiss: () -> Unit) {
    Surface(color = Color(0xFFFFEDF3), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), shape = RoundedCornerShape(13.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = dismiss, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun GuideComposer(game: GameEntity, imagePaths: List<String>, sending: Boolean, webSearch: Boolean, vm: GuideViewModel) {
    var text by rememberSaveable(game.id) { mutableStateOf("") }
    var attachMenu by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris -> vm.importImages(uris) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingCameraPath?.let(vm::acceptCameraPath)
        pendingCameraPath = null
    }
    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(), color = Color(0xFFFCFBFF),
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            if (imagePaths.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    items(imagePaths) { path ->
                        Box {
                            AsyncImage(File(path), null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            IconButton(onClick = { vm.removeImage(path) }, modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color(0xBB20202A), CircleShape)) {
                                Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    if (imagePaths.size < 5) item { Text("${imagePaths.size}/5", color = Color.Gray, modifier = Modifier.padding(20.dp, 4.dp)) }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                item { AssistChip(onClick = { vm.sendQuestion("현재 화면과 진행 상황을 기준으로 힌트 1만 줘", 1) }, label = { Text("힌트 1") }) }
                item { AssistChip(onClick = { vm.sendQuestion("현재 화면과 진행 상황을 기준으로 힌트 2를 줘", 2) }, label = { Text("힌트 2") }) }
                item { AssistChip(onClick = { vm.sendQuestion("현재 화면과 진행 상황을 기준으로 힌트 3을 줘", 3) }, label = { Text("힌트 3") }) }
                item { AssistChip(onClick = { vm.sendQuestion("정답과 정확한 해결 순서를 알려줘", 4) }, label = { Text("🏆 정답 보기") }) }
            }
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("여기서 뭐 해야 돼?") }, shape = RoundedCornerShape(19.dp), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { vm.sendQuestion(text); text = "" } }),
                trailingIcon = {
                    FilledIconButton(onClick = { if (text.isNotBlank()) { vm.sendQuestion(text); text = "" } }, enabled = !sending) {
                        if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Icon(Icons.AutoMirrored.Rounded.Send, "전송")
                    }
                }
            )
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { attachMenu = true }, enabled = imagePaths.size < 5) { Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(5.dp)); Text("사진 추가 ${imagePaths.size}/5") }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(text = { Text("갤러리·최근 스크린샷") }, leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) }, onClick = {
                            attachMenu = false; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text("카메라 촬영") }, leadingIcon = { Icon(Icons.Rounded.CameraAlt, null) }, onClick = {
                            attachMenu = false
                            val (uri, path) = vm.createCameraTarget(); pendingCameraPath = path; camera.launch(uri)
                        })
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.setWebSearch(!webSearch) }) {
                    Icon(Icons.Rounded.Language, null, tint = if (webSearch) GuideBlue else Color.Gray)
                    Spacer(Modifier.width(5.dp)); Text(if (webSearch) "웹 검색 켬" else "웹 검색 자동", color = if (webSearch) GuideBlue else Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun AddGameDialog(onDismiss: () -> Unit, onAdd: (GameEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf(Platform.PS5) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("게임 등록", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("게임명") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("플랫폼: ${platform.label}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        Platform.entries.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { platform = item; menu = false }) }
                    }
                }
                Text("등록 후 진행도와 대표 이미지를 편집할 수 있습니다.", color = Color.Gray, fontSize = 13.sp)
            }
        },
        confirmButton = { Button(onClick = { onAdd(GameEntity(name = name.trim(), platform = platform.label)) }, enabled = name.isNotBlank()) { Text("등록") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun ProgressDialog(game: GameEntity, onDismiss: () -> Unit, onSave: (GameEntity) -> Unit, onDelete: () -> Unit) {
    var chapter by remember { mutableStateOf(game.chapter) }
    var region by remember { mutableStateOf(game.region) }
    var quest by remember { mutableStateOf(game.mainQuest) }
    var hours by remember { mutableStateOf(game.playHours.toString()) }
    var progress by remember { mutableStateOf(game.progressPercent.toFloat()) }
    var spoiler by remember { mutableStateOf(runCatching { SpoilerLevel.valueOf(game.spoilerLevel) }.getOrDefault(SpoilerLevel.NONE)) }
    var playStyle by remember { mutableStateOf(runCatching { PlayStyle.valueOf(game.playStyle) }.getOrDefault(PlayStyle.BALANCED)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${game.name} 진행도", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedTextField(chapter, { chapter = it }, label = { Text("현재 챕터") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(region, { region = it }, label = { Text("현재 지역") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(quest, { quest = it }, label = { Text("현재 메인 퀘스트") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(hours, { hours = it }, label = { Text("플레이 시간") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()) }
                item {
                    Text("예상 진행률 ${progress.toInt()}%", fontWeight = FontWeight.Bold)
                    Slider(progress, { progress = it }, valueRange = 0f..100f)
                }
                item {
                    Text("스포일러 수준", fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(SpoilerLevel.entries) { item -> AssistChip(onClick = { spoiler = item }, label = { Text(item.label) }, leadingIcon = if (item == spoiler) ({ Icon(Icons.Rounded.Shield, null, Modifier.size(16.dp)) }) else null) }
                    }
                }
                item {
                    Text("플레이 스타일", fontWeight = FontWeight.Bold)
                    Column { PlayStyle.entries.forEach { item -> TextButton(onClick = { playStyle = item }) { Text(if (item == playStyle) "✓ ${item.label}" else item.label) } } }
                }
                item { TextButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(5.dp)); Text("게임 삭제", color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = { Button(onClick = { onSave(game.copy(chapter = chapter, region = region, mainQuest = quest, playHours = hours.toFloatOrNull() ?: game.playHours, progressPercent = progress.toInt(), spoilerLevel = spoiler.name, playStyle = playStyle.name)) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun AiSettingsDialog(vm: GuideViewModel, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(vm.currentModel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 연결 설정", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (vm.hasApiKey) "API 키가 안전하게 저장되어 있습니다." else "AI 공략을 사용하려면 OpenAI API 키가 필요합니다.", color = if (vm.hasApiKey) GuideBlue else Color.Gray)
                OutlinedTextField(key, { key = it }, label = { Text(if (vm.hasApiKey) "새 키로 변경 (선택)" else "OpenAI API 키") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("모델") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("키는 Android Keystore로 암호화되며 앱 코드와 Room DB에는 저장되지 않습니다.", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = { Button(onClick = { vm.saveAiSettings(key, model); onDismiss() }, enabled = vm.hasApiKey || key.isNotBlank()) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}
