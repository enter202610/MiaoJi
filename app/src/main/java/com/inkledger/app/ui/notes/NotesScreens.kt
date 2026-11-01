package com.inkledger.app.ui.notes

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkledger.app.AppViewModel
import com.inkledger.app.data.db.NoteEntity
import com.inkledger.app.data.media.NotesMedia
import com.inkledger.app.ui.components.EmptyHint
import com.inkledger.app.ui.components.InkAlertDialog
import com.inkledger.app.ui.components.InkCard
import com.inkledger.app.ui.components.InkDialogButton
import com.inkledger.app.ui.components.PageHeader
import com.inkledger.app.util.nowMillis
import com.inkledger.app.util.toLocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- 正文内嵌图片：[img:文件名|s|m|l] ----------

private val IMG_REGEX = Regex("""\[img:([^\]|]+)\|([sml])\]""")

private sealed class Seg {
    data class Text(val text: String) : Seg()
    data class Img(val name: String, val size: Char) : Seg()
}

/** 编辑态分段：文字段（可输入）与图片段（真实显示）交错；id 用于稳定复用输入框状态 */
private data class EditSeg(
    val text: String = "",
    val img: String? = null,
    val size: Char = 'm',
    val id: Long = 0L
)

private fun parseBody(body: String): List<Seg> {
    val out = mutableListOf<Seg>()
    var last = 0
    IMG_REGEX.findAll(body).forEach { m ->
        if (m.range.first > last) out += Seg.Text(body.substring(last, m.range.first))
        out += Seg.Img(m.groupValues[1], m.groupValues[2][0])
        last = m.range.last + 1
    }
    if (last < body.length) out += Seg.Text(body.substring(last))
    return out
}

private fun bodyToSegments(body: String): MutableList<EditSeg> {
    val out = mutableListOf<EditSeg>()
    var nextId = 1L
    var last = 0
    IMG_REGEX.findAll(body).forEach { m ->
        if (m.range.first > last) out += EditSeg(text = body.substring(last, m.range.first), id = nextId++)
        out += EditSeg(img = m.groupValues[1], size = m.groupValues[2][0], id = nextId++)
        last = m.range.last + 1
    }
    if (last < body.length) out += EditSeg(text = body.substring(last), id = nextId++)
    if (out.isEmpty()) out += EditSeg(id = nextId)
    return out
}

private fun segmentsToBody(segs: List<EditSeg>): String =
    segs.joinToString("") { s ->
        if (s.img != null) "[img:${s.img}|${s.size}]" else s.text
    }

private fun bodyWithoutMarkers(body: String): String = IMG_REGEX.replace(body, " ")

private fun firstImageName(body: String): String? =
    IMG_REGEX.find(body)?.groupValues?.get(1)

// ---------- 列表 ----------

@Composable
fun NotesScreen(vm: AppViewModel, onOpenNote: (Long) -> Unit) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    var noteQuery by remember { mutableStateOf("") }
    var deleteNoteTarget by remember { mutableStateOf<NoteEntity?>(null) }
    val filtered = if (noteQuery.isBlank()) notes
    else notes.filter { it.title.contains(noteQuery, true) || it.body.contains(noteQuery, true) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            PageHeader(
                title = "记事",
                actions = {
                    IconButton(onClick = { onOpenNote(-1L) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建记事")
                    }
                }
            )
        }
        item {
            OutlinedTextField(
                value = noteQuery,
                onValueChange = { noteQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                placeholder = { Text("搜索记事…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (filtered.isEmpty()) {
            item {
                EmptyHint(
                    "📝",
                    if (notes.isEmpty()) "还没有记事" else "没有匹配的记事",
                    "灵感、待办、购物清单……随笔记下"
                )
            }
        }
        items(filtered, key = { it.id }) { n ->
            com.inkledger.app.ui.components.SwipeToDeleteBox(
                onDelete = { deleteNoteTarget = n }
            ) {
            InkCard(
                Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .clickable { onOpenNote(n.id) }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        firstImageName(n.body)?.let { name ->
                            NoteImage(
                                name = name,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                maxDim = 320
                            )
                            Spacer(Modifier.size(10.dp))
                        }
                        if (n.pinned) {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = "已置顶",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(14.dp)
                            )
                        }
                        Text(
                            n.title.ifBlank { "无标题" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            (n.createdAt.takeIf { it > 1000000000000 } ?: n.updatedAt).toLocalDateTime()
                                .format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val preview = bodyWithoutMarkers(n.body)
                    if (preview.isNotBlank()) {
                        Text(
                            preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            }
        }
        item { Spacer(Modifier.size(96.dp)) }
    }

    deleteNoteTarget?.let { n ->
        InkAlertDialog(
            onDismissRequest = { deleteNoteTarget = null },
            title = { Text("删除「${n.title.ifBlank { "无标题" }}」？") },
            text = { Text("记事及内嵌图片会被删除，无法恢复。") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkDialogButton("取消", onClick = { deleteNoteTarget = null })
                    InkDialogButton(
                        "删除",
                        danger = true,
                        onClick = {
                            vm.deleteNote(n)
                            deleteNoteTarget = null
                        }
                    )
                }
            },
            dismissButton = {}
        )
    }
}

/**
 * 记事编辑：查看态渲染图文；编辑态为分段编辑器——
 * 文字段直接输入，图片段按正文位置真实显示（上传即见），回车自动续列表。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteEditScreen(
    vm: AppViewModel,
    noteId: Long,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var note by remember { mutableStateOf<NoteEntity?>(null) }
    var segments by remember { mutableStateOf(mutableListOf(EditSeg())) }
    var loaded by remember { mutableStateOf(noteId <= 0L) }
    // 打开即是可编辑：旧记事也直接进入编辑，不必再点一次「编辑」
    var editing by remember { mutableStateOf(true) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var focusIdx by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()

    // 光标跟随（市面笔记做法）：输入/聚焦时把光标所在行以最小滚动量送入可视区——
    // 光标可见就一动不动，快被输入法盖住才微滚（绝不跳到底）
    val density = LocalDensity.current
    val bringReqs = remember { androidx.compose.runtime.mutableStateMapOf<Long, androidx.compose.foundation.relocation.BringIntoViewRequester>() }
    val textLayouts = remember { mutableMapOf<Long, androidx.compose.ui.text.TextLayoutResult>() }
    fun revealCursor(segId: Long, offset: Int) {
        // 纯几何规则（与文档位置无关）：本次编辑后光标所在行/下一行
        // 若会被输入法盖住 → 最小滚动量滚出；在可视区内 → 一动不动
        val tl = textLayouts[segId] ?: return
        val off = offset.coerceAtMost(tl.layoutInput.text.length)
        if (off < 0) return
        val cr = try { tl.getCursorRect(off) } catch (e: Exception) { return }
        // 预留约两行提前量：下一行将进入输入法区域时就滚，不等被盖住
        val lookAhead = with(density) { 28.sp.toPx() } * 2f
        scope.launch {
            bringReqs[segId]?.bringIntoView(
                androidx.compose.ui.geometry.Rect(
                    0f, cr.top - 8f,
                    tl.size.width.toFloat().coerceAtLeast(1f), cr.bottom + lookAhead
                )
            )
        }
    }

    // 光标与焦点控制：按段 id 记录输入框的光标值 / 焦点请求器
    val tfvState = remember { androidx.compose.runtime.mutableStateMapOf<Long, TextFieldValue>() }
    val focusReqs = remember { androidx.compose.runtime.mutableStateMapOf<Long, FocusRequester>() }
    var nextSegId by remember { mutableStateOf(100_000L) }
    fun newSegId(): Long = nextSegId++

    val composedBody = segmentsToBody(segments)

    LaunchedEffect(noteId) {
        if (noteId > 0L) {
            vm.getNote(noteId)?.let { n ->
                note = n
                segments = bodyToSegments(n.body)
            }
            loaded = true
        }
    }

    fun autoTitle(): String {
        val firstLine = bodyWithoutMarkers(composedBody).trim()
            .lines().firstOrNull { it.isNotBlank() } ?: ""
        return if (firstLine.length > 16) firstLine.take(16) + "…" else firstLine
    }

    fun current() = NoteEntity(
        id = note?.id ?: 0L,
        title = autoTitle(),
        body = composedBody,
        pinned = note?.pinned ?: false,
        createdAt = note?.createdAt ?: 0L,  // 创建后不变
        updatedAt = nowMillis(),
        media = segments.filter { it.img != null }.joinToString(",") { it.img!! }
    )

    LaunchedEffect(segments, loaded) {
        if (!loaded) return@LaunchedEffect
        if (composedBody.isBlank()) return@LaunchedEffect
        delay(600)
        val id = vm.saveNoteSuspend(current())
        if (note == null) {
            // 首次保存：锁定创建时间
            note = current().copy(id = id, createdAt = nowMillis())
        }
    }

    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val camLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) pendingCameraFile?.let { f -> pendingImage = f.name }
        pendingCameraFile = null
    }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { u -> NotesMedia.copyFromUri(context, u)?.let { n -> pendingImage = n } }
    }

    /** 在当前聚焦文字段之后插入图片段 + 新文字段，光标自动移到图片后面的新段落 */
    fun insertImageAtFocus(name: String, size: Char) {
        val idx = focusIdx.coerceIn(0, segments.size - 1)
        val afterId = newSegId()
        val newList = segments.toMutableList()
        newList.add(idx + 1, EditSeg(img = name, size = size, id = newSegId()))
        newList.add(idx + 2, EditSeg(id = afterId))
        tfvState[afterId] = TextFieldValue("")
        segments = newList
        focusIdx = idx + 2
        // 滚动到图片下方并请求焦点，光标落在图片后
        scope.launch {
            delay(80)
            focusReqs[afterId]?.requestFocus()
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    /** 文字段内容变化 + 回车自动续列表（续出的编号后光标定位到行尾） */
    fun updateSegment(idx: Int, new: String) {
        val old = segments.getOrNull(idx)?.text ?: return
        if (new == old) return
        var out = new
        if (new.length == old.length + 1 && new.endsWith("\n")) {
            val before = new.dropLast(1)
            val lineStart = before.lastIndexOf('\n') + 1
            val line = before.substring(lineStart)
            val num = Regex("""^(\d+)\.\s(.*)$""").find(line)
            val isBullet = line.startsWith("- ")
            out = when {
                num != null && num.groupValues[2].isNotEmpty() ->
                    new + "${num.groupValues[1].toInt() + 1}. "
                isBullet && line.length > 2 -> new + "- "
                (num != null && num.groupValues[2].isEmpty()) || (isBullet && line.length == 2) ->
                    before.substring(0, lineStart)
                else -> new
            }
        }
        val newList = segments.toMutableList()
        newList[idx] = newList[idx].copy(text = out)
        segments = newList
        // 文本被程序改写（如续出 "2. "）时，光标同步到末尾
        if (out != new) {
            val segId = newList[idx].id
            tfvState[segId] = TextFieldValue(out, TextRange(out.length))
        }
    }

    fun insertListPrefix(prefix: String) {
        val idx = focusIdx.coerceIn(0, segments.size - 1)
        val seg = segments[idx]
        if (seg.img != null) return
        val t = seg.text
        val pos = tfvState[seg.id]?.selection?.min ?: t.length
        val lineStart = t.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).plus(1)
        val newText = t.substring(0, lineStart) + prefix + t.substring(lineStart)
        val newList = segments.toMutableList()
        newList[idx] = newList[idx].copy(text = newText)
        segments = newList
        // 光标移到插入的列表符后面，并重新聚焦（点工具栏会失焦）
        val caret = lineStart + prefix.length
        tfvState[seg.id] = TextFieldValue(newText, TextRange(caret))
        scope.launch {
            delay(30)
            focusReqs[seg.id]?.requestFocus()
        }
    }

    /** 删除图片段：合并前后相邻的文字段，避免留下「退格删不掉」的隐形边界 */
    fun removeImageAt(idx: Int, fileName: String) {
        val newList = segments.toMutableList()
        newList.removeAt(idx)
        // 前后都是文字段 → 合并成一个，光标落在原分界处
        if (idx > 0 && idx < newList.size &&
            newList[idx].img == null && newList[idx - 1].img == null
        ) {
            val a = newList[idx - 1]
            val b = newList[idx]
            val merged = a.text + b.text
            tfvState[a.id] = TextFieldValue(merged, TextRange(a.text.length))
            tfvState.remove(b.id)
            focusReqs.remove(b.id)
            newList.removeAt(idx)
            newList[idx - 1] = a.copy(text = merged)
        }
        segments = newList
        NotesMedia.delete(context, fileName)
    }

    fun flushAndBack() {
        scope.launch {
            if (composedBody.isNotBlank()) vm.saveNoteSuspend(current())
            onBack()
        }
    }

    // 语音输入：拉起系统识别页 → 结果追加到当前聚焦段（或末尾）
    val noteVoice = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                val target = if (focusIdx < segments.size && segments[focusIdx].img == null) focusIdx
                else segments.indexOfLast { it.img == null }.takeIf { it >= 0 } ?: segments.size
                val newList = segments.toMutableList()
                if (target >= newList.size) newList.add(EditSeg(id = newSegId()))
                val seg = newList[target]
                val merged = seg.text + (if (seg.text.isNotBlank() && !seg.text.endsWith("\n")) "\n" else "") + text
                newList[target] = seg.copy(text = merged)
                segments = newList
                tfvState[seg.id] = TextFieldValue(merged, TextRange(merged.length))
                focusIdx = target
                scope.launch {
                    kotlinx.coroutines.delay(60)
                    focusReqs[seg.id]?.requestFocus()
                }
            } else android.widget.Toast.makeText(context, "没听清，再试一次", Toast.LENGTH_SHORT).show()
        }
    }
    fun startNoteVoice() {
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        }
        try {
            noteVoice.launch(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "此设备没有可用的语音识别服务", Toast.LENGTH_SHORT).show()
        }
    }

    // 键盘高度探测：HyperOS 在全面屏 edge-to-edge 下可能不把 ime insets 报给应用
    // （logcat 实锤 scroll 已到底但内容仍在键盘下），用可见区域反推键盘高度兜底
    val rootView = LocalView.current
    var frameImePx by remember { mutableIntStateOf(0) }
    DisposableEffect(rootView) {
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val r = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val rootH = rootView.rootView.height
            val kb = rootH - r.bottom
            // 阈值 1/4 屏高：过滤导航栏等小 inset，只在真键盘弹出时算数
            frameImePx = if (kb > rootH / 4) kb else 0
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { rootView.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    val imeInsetPx = WindowInsets.ime.getBottom(density)
    val imePadPx = maxOf(imeInsetPx, frameImePx)
    androidx.compose.runtime.LaunchedEffect(imeInsetPx, frameImePx) {
        android.util.Log.d("InkNote", "imeInset=$imeInsetPx frameKb=$frameImePx pad=$imePadPx")
    }

    Column(Modifier.fillMaxSize().padding(bottom = with(density) { imePadPx.toDp() })) {
        PageHeader(
            title = "记事",
            onBack = { flushAndBack() },
            actions = {
                if (!editing) {
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                    }
                } else {
                    IconButton(onClick = { editing = false }) {
                        Icon(Icons.Rounded.Visibility, contentDescription = "切换到阅读视图")
                    }
                    IconButton(onClick = {
                        val f = NotesMedia.newCameraFile(context)
                        pendingCameraFile = f
                        camLauncher.launch(NotesMedia.uriFor(context, f))
                    }) { Icon(Icons.Rounded.PhotoCamera, contentDescription = "拍照插入") }
                    IconButton(onClick = {
                        pickLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Icon(Icons.Rounded.PhotoLibrary, contentDescription = "从相册插入") }
                    IconButton(onClick = { insertListPrefix("1. ") }) {
                        Icon(Icons.Rounded.FormatListNumbered, contentDescription = "有序列表")
                    }
                    IconButton(onClick = { insertListPrefix("- ") }) {
                        Icon(Icons.Rounded.FormatListBulleted, contentDescription = "无序列表")
                    }
                }
                IconButton(onClick = {
                    val newPinned = !(note?.pinned ?: false)
                    note = note?.copy(pinned = newPinned)
                    val n = note
                    if (n != null) scope.launch { vm.saveNoteSuspend(n) }
                }) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = "置顶",
                        tint = if (note?.pinned == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    note?.let { vm.deleteNote(it) }
                    onBack()
                }) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        if (!editing) {
            // 阅读视图：渲染好的图文（从编辑态点「阅读」进入）
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                parseBody(composedBody).forEach { seg ->
                    when (seg) {
                        is Seg.Text -> if (seg.text.isNotBlank()) {
                            Text(
                                seg.text.trimStart('\n'),
                                style = TextStyle(fontSize = 17.sp, lineHeight = 28.sp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        is Seg.Img -> {
                            InlineImage(seg.name, seg.size) { viewing = seg.name }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        } else {
            // 分段编辑器：文字段可输入（任意位置点击即输入），图片段按正文位置真实显示
            // 点编辑区任意空白（非输入框处）→ 聚焦到最后一个文字段，光标到末尾
            val ruleStyle = TextStyle(fontSize = 17.sp, lineHeight = 28.sp)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            val lastIdx = segments.indexOfLast { it.img == null }
                            if (lastIdx < 0) return@detectTapGestures
                            focusIdx = lastIdx
                            val sid = segments[lastIdx].id
                            tfvState[sid]?.let {
                                tfvState[sid] = it.copy(selection = TextRange(it.text.length))
                            }
                            scope.launch {
                                kotlinx.coroutines.delay(40)
                                focusReqs[sid]?.requestFocus()
                            }
                        }
                    }
            ) {
                segments.forEachIndexed { idx, seg ->
                    key(seg.id) {
                        if (seg.img == null) {
                            val fr = remember { FocusRequester() }
                            focusReqs[seg.id] = fr
                            val br = remember(seg.id) { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                            bringReqs[seg.id] = br
                            val tfv = tfvState[seg.id]?.takeIf { it.text == seg.text }
                                ?: TextFieldValue(seg.text, TextRange(0))
                            BasicTextField(
                                value = tfv,
                                onValueChange = { nv ->
                                    tfvState[seg.id] = nv
                                    updateSegment(idx, nv.text)
                                    // 几何判断：光标行/下一行会被键盘盖住才滚
                                    revealCursor(seg.id, nv.selection.end)
                                },
                                onTextLayout = { textLayouts[seg.id] = it },
                                textStyle = ruleStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .bringIntoViewRequester(br)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            focusIdx = idx
                                            // 延迟读取：聚焦回调跑在点击落点更新之前，
                                            // 立即读会拿到旧光标（初始=文末）导致跳底
                                            scope.launch {
                                                kotlinx.coroutines.delay(350)
                                                tfvState[seg.id]?.let {
                                                    revealCursor(seg.id, it.selection.end)
                                                }
                                            }
                                        }
                                    }
                                    .focusRequester(fr),
                                decorationBox = { inner ->
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 2.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                                    ) {
                                        inner()
                                    }
                                }
                            )
                        } else {
                            Box {
                                InlineImage(seg.img, seg.size) { viewing = seg.img }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .background(
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                            CircleShape
                                        )
                                        .clickable { removeImageAt(idx, seg.img) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "移除图片",
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                // 点下方空白：聚焦最后一个文字段（末尾是图片时自动补一个新段），光标到末尾
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .pointerInput(segments.size) {
                            detectTapGestures {
                                val lastIdx = segments.lastIndex
                                if (lastIdx < 0) return@detectTapGestures
                                if (segments[lastIdx].img != null) {
                                    val newId = newSegId()
                                    segments = segments.toMutableList().apply { add(EditSeg(id = newId)) }
                                }
                                val target = segments.lastIndex
                                focusIdx = target
                                val sid = segments.getOrNull(target)?.id ?: return@detectTapGestures
                                tfvState[sid]?.let {
                                    tfvState[sid] = it.copy(selection = TextRange(it.text.length))
                                }
                                scope.launch {
                                    delay(60)
                                    focusReqs[sid]?.requestFocus()
                                }
                            }
                        }
                )
            }
        }
    }

    pendingImage?.let { name ->
        InkAlertDialog(
            onDismissRequest = { pendingImage = null },
            title = { Text("插入尺寸") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("s" to "小", "m" to "中", "l" to "大（通栏）").forEach { (code, label) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                insertImageAtFocus(name, code[0])
                                pendingImage = null
                            }
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                InkDialogButton("取消", onClick = { pendingImage = null })
            },
            dismissButton = {}
        )
    }

    viewing?.let { name ->
        ImageViewerDialog(name = name, onDismiss = { viewing = null })
    }
}

/** 正文内嵌图片：s=72dp / m=160dp / l=通栏保持比例；点击看原图 */
@Composable
private fun InlineImage(name: String, size: Char, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, name) {
        value = withContext(Dispatchers.IO) {
            NotesMedia.file(context, name)?.let { NotesMedia.decode(it, 900) }
        }
    }
    val base = Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClick = onClick)
    when (size) {
        's' -> NoteImage(name, base.size(72.dp), 480, onClick)
        'm' -> NoteImage(name, base.size(160.dp), 720, onClick)
        else -> {
            bmp?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = "记事图片（点开看原图）",
                    modifier = base
                        .fillMaxWidth()
                        .aspectRatio(it.width.toFloat() / it.height.toFloat()),
                    contentScale = ContentScale.Crop
                )
            } ?: Box(base.fillMaxWidth().height(120.dp))
        }
    }
}

@Composable
private fun NoteImage(
    name: String,
    modifier: Modifier = Modifier,
    maxDim: Int,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, name) {
        value = withContext(Dispatchers.IO) {
            NotesMedia.file(context, name)?.let { NotesMedia.decode(it, maxDim) }
        }
    }
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Box(m, contentAlignment = Alignment.Center) {
        bmp?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = "记事图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/** 全屏原图查看：可保存到相册（Android 10+） */
@Composable
private fun ImageViewerDialog(name: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    val bmp by produceState<Bitmap?>(initialValue = null, name) {
        value = withContext(Dispatchers.IO) {
            NotesMedia.file(context, name)?.let { NotesMedia.decode(it, 2048) }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xEE101216))
        ) {
            bmp?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = "原图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Text(
                            if (saved) "已保存到相册 ✓" else "保存到相册",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clickable { saved = NotesMedia.saveToGallery(context, name) }
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Text(
                        "关闭",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
