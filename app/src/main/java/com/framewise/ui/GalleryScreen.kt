package com.framewise.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.framewise.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.framewise.engine.PhotoCompositionEngine
import com.framewise.engine.PostCaptureAnalyzer
import com.framewise.engine.PostCaptureResult
import com.framewise.engine.rules.ALL_RULES

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Android 13+ uses the granular READ_MEDIA_IMAGES; older versions use
    // READ_EXTERNAL_STORAGE.
    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(readPermission)
    val granted = permissionState.status.isGranted

    val photos = remember { mutableStateListOf<Uri>() }
    val selectedPhotos = remember { mutableStateListOf<Uri>() }
    var loading by remember { mutableStateOf(true) }
    var selectedPhoto by remember { mutableStateOf<Uri?>(null) }
    var pendingDelete by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<PostCaptureResult?>(null) }
    var showAnalysisSheet by remember { mutableStateOf(false) }

    val selectionMode = selectedPhotos.isNotEmpty()

    // Request the permission as soon as we land on the gallery.
    LaunchedEffect(Unit) {
        if (!granted) permissionState.launchPermissionRequest()
    }

    // Load photos from MediaStore once permission is granted.
    LaunchedEffect(granted) {
        if (granted) {
            loading = true
            val loaded = loadFramewisePhotos(context)
            photos.clear()
            photos.addAll(loaded)
            selectedPhotos.clear()
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectionMode) "已选择 ${selectedPhotos.size} 张" else stringResource(R.string.gallery_title))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) selectedPhotos.clear() else onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = if (selectionMode) "退出选择" else "返回"
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = { pendingDelete = selectedPhotos.toList() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除已选择照片"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                !granted -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "需要相册权限才能显示已拍摄的照片",
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { permissionState.launchPermissionRequest() }) {
                            Text("授予权限")
                        }
                        Spacer(Modifier.height(16.dp))
                        // 引导用户去设置页
                        Text(
                            text = "如果已被拒绝，请前往系统设置 → 应用 → 构图指南 → 权限中手动开启",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                photos.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.no_photos),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    loading = true
                                    val loaded = loadFramewisePhotos(context)
                                    photos.clear(); photos.addAll(loaded); loading = false
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("刷新")
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(photos, key = { it.toString() }) { uri ->
                            val selected = uri in selectedPhotos
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(4.dp)
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionMode) {
                                                toggleSelection(selectedPhotos, uri)
                                            } else {
                                                selectedPhoto = uri
                                            }
                                        },
                                        onLongClick = { toggleSelection(selectedPhotos, uri) }
                                    )
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(uri),
                                    contentDescription = "已拍摄的照片",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "已选",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedPhoto?.let { uri ->
            PhotoViewer(
                uri = uri,
                onClose = { selectedPhoto = null },
                onShare = { sharePhoto(context, uri) },
                onDelete = { pendingDelete = listOf(uri) },
                onAnalyze = {
                    scope.launch {
                        isAnalyzing = true
                        val engine = PhotoCompositionEngine().registerRules(ALL_RULES)
                        val analyzer = PostCaptureAnalyzer(engine)
                        analysisResult = analyzer.analyzeCapturedPhoto(uri, context)
                        isAnalyzing = false
                        showAnalysisSheet = true
                    }
                }
            )
        }
    }

    if (pendingDelete.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDelete = emptyList() },
            title = { Text("删除照片") },
            text = { Text("确定删除 ${pendingDelete.size} 张照片吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targets = pendingDelete
                        pendingDelete = emptyList()
                        scope.launch {
                            val deleted = deletePhotos(context, targets)
                            photos.removeAll(deleted.toSet())
                            selectedPhotos.removeAll(deleted.toSet())
                            if (selectedPhoto in deleted) selectedPhoto = null
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = emptyList() }) {
                    Text("取消")
                }
            }
        )
    }

    // 构图分析结果 BottomSheet
    if (showAnalysisSheet && analysisResult != null) {
        val result = analysisResult!!
        ModalBottomSheet(
            onDismissRequest = { showAnalysisSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AnalysisResultContent(
                result = result,
                onDismiss = { showAnalysisSheet = false }
            )
        }
    }

    // 分析加载指示器
    if (isAnalyzing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("分析中") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("正在分析构图...")
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun PhotoViewer(
    uri: Uri,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onAnalyze: () -> Unit = {},
) {
    // 缩放/平移状态（参考 awesome-android-ui TouchImageView）
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Image(
            painter = rememberAsyncImagePainter(uri),
            contentDescription = "查看大图",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentScale = ContentScale.Fit
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回相册",
                tint = Color.White
            )
        }

        // 重置缩放按钮（当已缩放时显示）
        if (scale > 1.05f) {
            IconButton(
                onClick = { scale = 1f; offsetX = 0f; offsetY = 0f },
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重置缩放",
                    tint = Color.White
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.72f)
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onShare) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("分享")
                }
                Button(onClick = onAnalyze) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("分析构图")
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("删除")
                }
            }
        }
    }
}

private fun toggleSelection(selectedPhotos: MutableList<Uri>, uri: Uri) {
    if (uri in selectedPhotos) {
        selectedPhotos.remove(uri)
    } else {
        selectedPhotos.add(uri)
    }
}

private fun sharePhoto(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享照片"))
}

private suspend fun deletePhotos(context: Context, uris: List<Uri>): List<Uri> =
    withContext(Dispatchers.IO) {
        uris.filter { uri ->
            context.contentResolver.delete(uri, null, null) > 0
        }
    }

/**
 * Query MediaStore for images saved by Framewise (under Pictures/Framewise or
 * Pictures/构图指南), newest first. Runs on the IO dispatcher.
 */
private suspend fun loadFramewisePhotos(context: Context): List<Uri> =
    withContext(Dispatchers.IO) {
        val uris = mutableListOf<Uri>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)

        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR " +
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to
                arrayOf("%Framewise%", "%构图指南%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Images.Media.DATA} LIKE ? OR " +
                "${MediaStore.Images.Media.DATA} LIKE ?" to
                arrayOf("%Framewise%", "%构图指南%")
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, selection, args, sortOrder)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    uris.add(ContentUris.withAppendedId(collection, id))
                }
            }
        uris
    }

@Composable
private fun AnalysisResultContent(
    result: PostCaptureResult,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "构图分析",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(16.dp))

        // 整体评分
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "总体评分",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val scoreColor = when {
                result.overallScore >= 80 -> Color(0xFF4CAF50)
                result.overallScore >= 60 -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            }
            Text(
                text = "${result.overallScore.toInt()} / 100",
                style = MaterialTheme.typography.headlineMedium,
                color = scoreColor
            )
        }

        Spacer(Modifier.height(20.dp))

        // 改进建议
        Text(
            text = "改进建议",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))

        if (result.improvements.isEmpty()) {
            Text(
                text = "构图不错！无需改进建议。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            result.improvements.forEach { suggestion ->
                val icon = when (suggestion.severity) {
                    "critical" -> Icons.Default.Warning
                    else -> Icons.Default.Info
                }
                val iconColor = when (suggestion.severity) {
                    "critical" -> Color(0xFFF44336)
                    "warning" -> Color(0xFFFF9800)
                    else -> Color(0xFF4CAF50)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Text(
                        text = suggestion.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 建议调整
        if (result.suggestedCrop != null || result.suggestedRotation != null ||
            result.suggestedFilter != null || result.comparisonWithLive != null
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "建议调整",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            result.suggestedCrop?.let { crop ->
                Text(
                    text = "✂️ 推荐裁剪区域: (${String.format("%.0f", crop.x * 100)}%, ${String.format("%.0f", crop.y * 100)}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            result.suggestedRotation?.let { angle ->
                Text(
                    text = "🔄 推荐旋转: ${String.format("%.1f", angle)}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            result.suggestedFilter?.let { filter ->
                Text(
                    text = "🎨 推荐滤镜: $filter",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            result.comparisonWithLive?.let { comp ->
                Text(
                    text = "📊 $comp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("关闭")
        }
    }
}
