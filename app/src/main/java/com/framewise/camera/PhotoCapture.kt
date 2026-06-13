package com.framewise.camera

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Photo capture helper wrapping [ImageCapture] with MediaStore persistence.
 *
 * Photos are saved to `Pictures/Framewise/` with the prefix `FW_`
 * and a timestamp. The caller receives a content [Uri] for gallery display.
 */
object PhotoCapture {

    private const val TAG = "PhotoCapture"

    /**
     * Capture and persist a JPEG to [MediaStore].
     *
     * @param imageCapture  A bound CameraX [ImageCapture] instance.
     * @param context       Any Android Context (used for ContentResolver).
     * @param onPhotoSaved  Callback on the main thread with the content [Uri].
     */
    fun capture(
        imageCapture: ImageCapture,
        context: android.content.Context,
        onPhotoSaved: (Uri) -> Unit,
    ) {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "FW_$name")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Framewise")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues,
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    if (uri != null) {
                        // Mark as not pending so the gallery shows it immediately.
                        try {
                            context.contentResolver.update(
                                uri,
                                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                                null,
                                null,
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not clear IS_PENDING flag", e)
                        }
                        Log.d(TAG, "Photo saved: $uri")
                        onPhotoSaved(uri)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.imageCaptureError}", exception)
                }
            },
        )
    }
}
