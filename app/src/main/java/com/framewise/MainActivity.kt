package com.framewise

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.framewise.ui.navigation.NavGraph
import com.framewise.ui.theme.FramewiseTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * The app's single activity. Hosts the whole Compose UI and gates the
 * navigation graph behind a runtime CAMERA permission request. Once granted,
 * it hands off to [NavGraph] (camera ⇄ settings, gallery).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FramewiseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FramewiseApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FramewiseApp() {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    // Track whether we've already asked, so a first launch (where
    // shouldShowRationale is also false) isn't mistaken for a permanent denial.
    var hasRequested by rememberSaveable { mutableStateOf(false) }

    when {
        cameraPermission.status.isGranted -> {
            val navController = rememberNavController()
            NavGraph(navController = navController)
        }
        // Permanently denied: the OS will no longer show the system prompt.
        hasRequested && !cameraPermission.status.shouldShowRationale -> {
            CameraPermissionDenied()
        }
        else -> {
            CameraPermissionRequest(onRequest = {
                hasRequested = true
                cameraPermission.launchPermissionRequest()
            })
        }
    }
}

/** Pre-permission screen: explains why the camera is needed, then requests it. */
@Composable
private fun CameraPermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Framewise needs camera access",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "We analyze the live viewfinder on-device to guide your composition. " +
                "The image never leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text("Grant camera access")
        }
    }
}

/** Shown when the camera permission has been permanently denied. */
@Composable
private fun CameraPermissionDenied() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Camera permission was denied",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Camera permission was denied. Please enable it in " +
                "Settings → Apps → Framewise → Permissions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
