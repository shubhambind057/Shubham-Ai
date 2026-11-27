package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.AppLanguage
import com.example.ui.components.EditorialBadge
import com.example.ui.components.ErrorBanner
import com.example.ui.components.InfoNoticeBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioCard
import com.example.ui.theme.CautionGold
import com.example.ui.theme.CautionGoldBg
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg
import com.example.ui.viewmodel.VisionUiState
import java.util.concurrent.Executors

@Composable
fun CameraVisionScreen(
    selectedLanguage: AppLanguage,
    visionState: VisionUiState,
    hasCameraPermission: () -> Boolean,
    onAnalyzeBitmap: (Bitmap, String, AppLanguage) -> Unit,
    onResetVision: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraGranted by remember { mutableStateOf(hasCameraPermission()) }
    var permissionDeniedOnce by remember { mutableStateOf(false) }
    var cameraLiveActive by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var questionText by remember { mutableStateOf("Identify objects, read visible text/signs, and explain key details clearly.") }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            captureExecutor.shutdown()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        permissionDeniedOnce = !granted
        if (granted) {
            cameraLiveActive = true
            cameraError = null
        } else {
            cameraLiveActive = false
        }
    }

    // Zero-permission Android Photo Picker (Play Policy compliant; never requests READ_MEDIA_IMAGES)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            cameraLiveActive = false
            val decoded = decodeScaledBitmapFromUri(context, uri, 1280)
            if (decoded != null) {
                capturedBitmap = decoded
                cameraError = null
                onResetVision()
            } else {
                cameraError = "Could not decode the selected image."
            }
        }
    }

    val quickPrompts = listOf(
        "Read & translate all visible text/document",
        "Identify objects & explain what I'm looking at",
        "Check this error screen / hardware issue",
        "Verify labels, ingredients, or fine print"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                eyebrow = "CAMERA & MULTIMODAL VISION",
                title = "Live Visual Q&A",
                subtitle = "Camera activates only when you explicitly grant permission and tap Open Live Camera. Photo Picker requires zero storage permissions."
            )
        }

        // Permission & Source Controls Card
        item {
            StudioCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Visual Input Source",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    EditorialBadge(
                        text = if (cameraGranted) "CAMERA GRANTED" else "PERMISSION NEEDED",
                        containerColor = if (cameraGranted) VerifiedEmeraldBg else CautionGoldBg,
                        contentColor = if (cameraGranted) VerifiedEmerald else CautionGold
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (cameraLiveActive) {
                                cameraLiveActive = false
                            } else if (hasCameraPermission()) {
                                cameraGranted = true
                                cameraLiveActive = true
                                cameraError = null
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("camera_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (cameraLiveActive) Icons.Filled.StopCircle else Icons.Filled.CameraAlt,
                            contentDescription = "Toggle Camera",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (cameraLiveActive) "Stop Camera" else "Open Camera")
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("photo_picker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = "Pick Photo",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Photo Picker")
                    }
                }

                if (permissionDeniedOnce && !cameraGranted) {
                    Spacer(Modifier.height(10.dp))
                    InfoNoticeBanner(
                        title = "Camera Permission Denied",
                        body = "Shubham AI never activates the camera without permission. You can still use the zero-permission Photo Picker or grant Camera access in Android Settings."
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenAppSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("camera_open_settings_button")
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Android App Settings")
                    }
                }

                if (!cameraError.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    ErrorBanner(message = cameraError!!, onDismiss = { cameraError = null })
                }
            }
        }

        // Live CameraX Preview (STRICTLY gated by cameraGranted && cameraLiveActive)
        if (cameraGranted && cameraLiveActive) {
            item {
                StudioCard(accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) {
                    Text(
                        text = "LIVE CAMERA PREVIEW (ACTIVE)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianBg)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                            .build()
                                        imageCaptureRef = capture
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            capture
                                        )
                                    } catch (e: Exception) {
                                        Log.e("CameraVision", "CameraX bind failed", e)
                                        cameraError = "Camera hardware unavailable on this device: ${e.localizedMessage}"
                                        cameraLiveActive = false
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val capture = imageCaptureRef ?: return@Button
                            capture.takePicture(
                                captureExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        try {
                                            val raw = image.toBitmap()
                                            val rotation = image.imageInfo.rotationDegrees
                                            val rotated = rotateAndScaleBitmap(raw, rotation, 1280)
                                            ContextCompat.getMainExecutor(context).execute {
                                                capturedBitmap = rotated
                                                cameraLiveActive = false
                                                onResetVision()
                                            }
                                        } catch (e: Exception) {
                                            ContextCompat.getMainExecutor(context).execute {
                                                cameraError = "Frame capture failed: ${e.localizedMessage}"
                                            }
                                        } finally {
                                            image.close()
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        ContextCompat.getMainExecutor(context).execute {
                                            cameraError = "Capture error: ${exception.localizedMessage}"
                                        }
                                    }
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("camera_capture_frame_button")
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Capture Frame", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Capture Frame for Analysis")
                    }
                }
            }
        }

        // Captured / Picked Image Preview + Prompt
        if (capturedBitmap != null) {
            item {
                StudioCard {
                    Text(
                        text = "SELECTED VISUAL FOR ANALYSIS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = capturedBitmap!!.asImageBitmap(),
                        contentDescription = "Captured or picked image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickPrompts) { preset ->
                            FilterChip(
                                selected = questionText == preset,
                                onClick = { questionText = preset },
                                label = { Text(preset) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it },
                        label = { Text("Ask about this image (${selectedLanguage.label})") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vision_question_input")
                    )

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            capturedBitmap?.let { bmp ->
                                onAnalyzeBitmap(bmp, questionText, selectedLanguage)
                            }
                        },
                        enabled = visionState !is VisionUiState.Analyzing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("analyze_image_button")
                    ) {
                        if (visionState is VisionUiState.Analyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Analyzing with Gemini Vision...")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Analyze", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Analyze Image (${selectedLanguage.label})")
                        }
                    }
                }
            }
        }

        // Analysis Output
        when (visionState) {
            is VisionUiState.Success -> {
                item {
                    StudioCard(elevated = true, accentBorder = VerifiedEmerald.copy(alpha = 0.5f)) {
                        EditorialBadge(
                            text = "GEMINI VISION ANALYSIS",
                            containerColor = VerifiedEmeraldBg,
                            contentColor = VerifiedEmerald
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = visionState.answer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            is VisionUiState.Error -> {
                item {
                    ErrorBanner(message = visionState.message, onDismiss = onResetVision)
                }
            }
            else -> Unit
        }
    }
}

private fun decodeScaledBitmapFromUri(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        val maxOrig = maxOf(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
        var sample = 1
        while (maxOrig / sample > maxDim) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
    } catch (_: Exception) {
        null
    }
}

private fun rotateAndScaleBitmap(source: Bitmap, rotationDegrees: Int, maxDim: Int): Bitmap {
    val matrix = Matrix()
    if (rotationDegrees != 0) {
        matrix.postRotate(rotationDegrees.toFloat())
    }
    val maxSide = maxOf(source.width, source.height).coerceAtLeast(1)
    if (maxSide > maxDim) {
        val scale = maxDim.toFloat() / maxSide.toFloat()
        matrix.postScale(scale, scale)
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
