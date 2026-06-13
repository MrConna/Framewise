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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    }
                }

                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                photos.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_photos),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center)
                    )
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
                onDelete = { pendingDelete = listOf(uri) }
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
}

@Composable
private fun PhotoViewer(
    uri: Uri,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Image(
            painter = rememberAsyncImagePainter(uri),
            contentDescription = "查看大图",
            modifier = Modifier.fillMaxSize(),
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

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color.White
            )
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
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("分享")
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null
                    )
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
