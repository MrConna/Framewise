package com.framewise.camera

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Observable camera state emitted by [CameraController].
 */
data class CameraState(
    val isReady: Boolean = false,
    val rotationDegrees: Int = 0,
    val isFrontCamera: Boolean = false,
    val error: String? = null,
    /** Set when CameraX binding fails, so the UI can show a recoverable banner. */
    val errorMessage: String? = null,
)

/**
 * Manages the CameraX lifecycle for the composition guide.
 *
 * Binds [Preview] and [ImageAnalysis] to a [ProcessCameraProvider], exposing
 * the raw YUV frames through [frameAnalyzer] and a shutter through [imageCapture].
 *
 * Typical usage from a Fragment:
 * ```
 * class CameraFragment : Fragment() {
 *     private lateinit var controller: CameraController
 *
 *     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *         val previewView = view.findViewById<PreviewView>(R.id.preview_view)
 *         controller = CameraController(
 *             lifecycleOwner = viewLifecycleOwner,
 *             previewView = previewView,
 *             frameAnalyzer = FrameAnalyzer(imageProcessor),
 *         )
 *         controller.bindToLifecycle()
 *     }
 * }
 * ```
 */
class CameraController(
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val frameAnalyzer: FrameAnalyzer,
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "CameraController"
    }

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /** Start the camera preview and image analysis. */
    fun start() {
        Log.d(TAG, "Starting camera…")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                bindUseCases(provider)
            } catch (e: Exception) {
                Log.e(TAG, "Camera init failed", e)
                _state.value = _state.value.copy(isReady = false, error = e.message)
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    /** Stop the camera and release resources. */
    fun stop() {
        Log.d(TAG, "Stopping camera…")
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
        _state.value = CameraState()
    }

    /**
     * Registers [start]/[stop] as an observer on the lifecycle, so camera
     * automatically binds on START and unbinds on STOP. Idempotent.
     */
    fun bindToLifecycle() {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    /**
     * Unregisters the lifecycle observer. Called when the composable leaves
     * composition (e.g. navigating to Settings) so we don't accumulate multiple
     * observers that fight over the same [ProcessCameraProvider] — the root
     * cause of the black screen on return. CameraX unbinds use cases on its own
     * when the lifecycle stops; we deliberately do NOT release the analyzer here.
     */
    fun unbindFromLifecycle() {
        lifecycleOwner.lifecycle.removeObserver(this)
    }

    /**
     * Force a (re)bind attempt. Used by the shutter when [imageCapture] is still
     * null because the provider hasn't finished initializing yet.
     */
    fun requestBinding() {
        Log.d(TAG, "requestBinding() — imageCapture=${if (imageCapture == null) "null" else "ready"}")
        val provider = cameraProvider
        if (provider == null) {
            start()
        } else if (imageCapture == null) {
            bindUseCases(provider)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        start()
    }

    override fun onStop(owner: LifecycleOwner) {
        stop()
    }

    // ── Camera controls ────────────────────────────────────────────────────

    /** Switch between front and back camera. Retains [state.isFrontCamera]. */
    fun flipCamera() {
        val current = _state.value.isFrontCamera
        _state.value = _state.value.copy(isFrontCamera = !current)
        cameraProvider?.let { provider ->
            provider.unbindAll()
            bindUseCases(provider)
        }
    }

    /**
     * Capture a photo using [ImageCapture] and save to [MediaStore].
     *
     * @param onPhotoSaved called on the main thread with the content [Uri].
     */
    fun takePhoto(onPhotoSaved: (Uri) -> Unit) {
        Log.d(TAG, "takePhoto invoked: imageCapture=${if (imageCapture == null) "null" else "ready"}, " +
                "cameraReady=${_state.value.isReady}")
        val capture = imageCapture ?: run {
            Log.e(TAG, "Cannot take photo: imageCapture is null — camera not bound yet")
            _state.value = _state.value.copy(error = "Camera not ready — try again in a moment")
            return
        }

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "FW_$name")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Framewise")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            previewView.context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues,
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(previewView.context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    if (uri != null) {
                        Log.d(TAG, "Photo saved: $uri")
                        onPhotoSaved(uri)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    _state.value = _state.value.copy(error = exception.message)
                }
            },
        )
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun bindUseCases(provider: ProcessCameraProvider) {
        val isFront = _state.value.isFrontCamera
        val lensFacing = if (isFront) CameraSelector.LENS_FACING_FRONT
                         else CameraSelector.LENS_FACING_BACK

        // ── Preview ─────────────────────────────────────────────────────
        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector())
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        // ── ImageAnalysis ───────────────────────────────────────────────
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(cameraExecutor, frameAnalyzer) }

        // ── ImageCapture ────────────────────────────────────────────────
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector())
            .build()

        // ── CameraSelector ──────────────────────────────────────────────
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        // ── Bind all three use cases ────────────────────────────────────
        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                analysis,
                imageCapture,
            )

            // Read sensor rotation (orientation degrees for EXIF).
            val rotation = camera?.cameraInfo?.sensorRotationDegrees ?: 0

            _state.value = CameraState(
                isReady = true,
                rotationDegrees = rotation,
                isFrontCamera = isFront,
            )
            Log.d(TAG, "Camera bound, rotation=$rotation, facing=${if (isFront) "front" else "back"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera", e)
            _state.value = _state.value.copy(
                isReady = false,
                error = e.message,
                errorMessage = e.message ?: "Unknown camera error",
            )
        }
    }

    /** Prefer display-size resolution with 16:9 aspect ratio fallback. */
    private fun resolutionSelector(): ResolutionSelector {
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
    }
}
