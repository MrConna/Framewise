package com.framewise.ui

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun GalleryScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

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
    var loading by remember { mutableStateOf(true) }
    var selectedPhoto by remember { mutableStateOf<Uri?>(null) }

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
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
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
                        items(photos) { uri ->
                            Image(
                                painter = rememberAsyncImagePainter(uri),
                                contentDescription = "已拍摄的照片",
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(4.dp)
                                    .clickable { selectedPhoto = uri },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // Full-screen viewer when a photo is tapped.
        selectedPhoto?.let { uri ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { selectedPhoto = null }
            ) {
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = "查看大图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { selectedPhoto = null },
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
            }
        }
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
