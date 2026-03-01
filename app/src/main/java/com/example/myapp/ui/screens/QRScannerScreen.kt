package com.example.myapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.example.myapp.ui.theme.GreenPrimary
import com.example.myapp.ui.theme.GreenPrimaryDark
import com.example.myapp.ui.theme.GreenSurface
import com.example.myapp.ui.theme.IPETheme
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.LifecycleOwner

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QRScannerScreen(
    onQRCodeScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val (cameraProvider, setCameraProvider) = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val (isCameraInitializing, setIsCameraInitializing) = remember { mutableStateOf(false) }
    val (cameraError, setCameraError) = remember { mutableStateOf<String?>(null) }

    // Check if permission is permanently denied
    val isPermissionPermanentlyDenied = remember(cameraPermissionState) {
        !cameraPermissionState.status.isGranted && 
        !cameraPermissionState.status.shouldShowRationale
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    // Initialize camera when permission is granted
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted && cameraProvider == null) {
            initializeCamera(
                context = context,
                lifecycleOwner = lifecycleOwner,
                onQRCodeScanned = onQRCodeScanned,
                setCameraProvider = setCameraProvider,
                setIsCameraInitializing = setIsCameraInitializing,
                setCameraError = setCameraError
            )
        }
    }

    // Cleanup camera when permission is denied or screen is closed
    DisposableEffect(cameraPermissionState.status.isGranted) {
        if (!cameraPermissionState.status.isGranted && cameraProvider != null) {
            cleanupCamera(cameraProvider)
            setCameraProvider(null)
        }
        onDispose { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            isCameraInitializing -> {
                // Show loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = GreenPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            cameraError != null -> {
                // Show error state
                ErrorContent(
                    errorMessage = cameraError!!,
                    onRetry = {
                        setCameraError(null)
                        initializeCamera(
                            context = context,
                            lifecycleOwner = lifecycleOwner,
                            onQRCodeScanned = onQRCodeScanned,
                            setCameraProvider = setCameraProvider,
                            setIsCameraInitializing = setIsCameraInitializing,
                            setCameraError = setCameraError
                        )
                    },
                    onBack = onBack
                )
            }
            cameraPermissionState.status.isGranted -> {
                // Show camera preview
                if (cameraProvider != null) {
                    CameraViewScreen(
                        cameraProvider = cameraProvider,
                        onQRCodeScanned = onQRCodeScanned,
                        onBack = onBack
                    )
                }
            }
            cameraPermissionState.status.shouldShowRationale -> {
                // Show rationale
                PermissionDeniedContent(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                    onBack = onBack
                )
            }
            isPermissionPermanentlyDenied -> {
                // Show permanently denied state
                PermissionPermanentlyDeniedContent(
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = android.net.Uri.parse("package:" + context.packageName)
                        context.startActivity(intent)
                    },
                    onBack = onBack
                )
            }
            else -> {
                // Show initial permission request
                PermissionDeniedContent(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                    onBack = onBack
                )
            }
        }
    }
}

private fun initializeCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onQRCodeScanned: (String) -> Unit,
    setCameraProvider: (ProcessCameraProvider?) -> Unit,
    setIsCameraInitializing: (Boolean) -> Unit,
    setCameraError: (String?) -> Unit
) {
    setIsCameraInitializing(true)
    setCameraError(null)
    
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // Setup camera preview and image analysis
                val preview = CameraPreview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(PreviewView(context).surfaceProvider)
                    }
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(
                            Executors.newSingleThreadExecutor()
                        ) { imageProxy ->
                            val buffer = imageProxy.planes[0].buffer
                            val data = ByteArray(buffer.remaining())
                            buffer.get(data)
                            buffer.rewind()

                            val source = PlanarYUVLuminanceSource(
                                data,
                                imageProxy.width,
                                imageProxy.height,
                                0,
                                0,
                                imageProxy.width,
                                imageProxy.height,
                                false
                            )

                            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                            val reader = MultiFormatReader()

                            try {
                                val result = reader.decode(binaryBitmap)
                                onQRCodeScanned(result.text)
                            } catch (e: NotFoundException) {
                                // No QR code found
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                // Bind camera use cases
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )

                setCameraProvider(cameraProvider)
                setIsCameraInitializing(false)
            } catch (e: Exception) {
                setCameraError("Failed to initialize camera: ${e.message}")
                setIsCameraInitializing(false)
            }
        }, ContextCompat.getMainExecutor(context))
}

private fun cleanupCamera(cameraProvider: ProcessCameraProvider) {
    try {
        cameraProvider.unbindAll()
    } catch (e: Exception) {
        // Log error but don't crash
        e.printStackTrace()
    }
}

@Composable
private fun CameraViewScreen(
    cameraProvider: ProcessCameraProvider,
    onQRCodeScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            
            // Setup camera preview with the existing provider
            val preview = CameraPreview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    // Overlay with scanner frame
    ScannerOverlay(
        onBack = onBack
    )
}

@Composable
private fun ErrorContent(
    errorMessage: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = GreenPrimary
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Error icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = "Error",
                tint = Color.Red,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Camera Error",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Red,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = errorMessage,
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
        ) {
            Text(
                text = "Retry",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onBack) {
            Text(
                text = "Go Back",
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun PermissionPermanentlyDeniedContent(
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = GreenPrimary
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Warning icon
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(Color.Yellow.copy(alpha = 0.2f), RoundedCornerShape(40.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Warning",
                                tint = Color.Yellow,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Permission Permanently Denied",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Yellow,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "You have permanently denied camera permission. Please enable it in app settings.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow)
                        ) {
            Text(
                text = "Open Settings",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onBack) {
            Text(
                text = "Cancel",
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun CameraPreviewAlternative(
    onQRCodeScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scannedCode by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                     val preview = CameraPreview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(
                                Executors.newSingleThreadExecutor()
                            ) { imageProxy ->
                                val buffer = imageProxy.planes[0].buffer
                                val data = ByteArray(buffer.remaining())
                                buffer.get(data)
                                buffer.rewind()

                                val source = PlanarYUVLuminanceSource(
                                    data,
                                    imageProxy.width,
                                    imageProxy.height,
                                    0,
                                    0,
                                    imageProxy.width,
                                    imageProxy.height,
                                    false
                                )

                                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                val reader = MultiFormatReader()

                                try {
                                    val result = reader.decode(binaryBitmap)
                                    if (scannedCode == null) {
                                        scannedCode = result.text
                                        onQRCodeScanned(result.text)
                                    }
                                } catch (e: NotFoundException) {
                                    // No QR code found
                                } finally {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay with scanner frame
        ScannerOverlay(
            onBack = onBack
        )
    }
}

@Composable
private fun ScannerOverlay(
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .background(Color.Black.copy(alpha = 0.6f))
        )

        // Middle section with scanner frame
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.3f)
                    .background(Color.Black.copy(alpha = 0.6f))
            )

            // Scanner frame
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.3f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(3.dp, GreenPrimary, RoundedCornerShape(16.dp))
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.3f)
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        }

        // Bottom section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .background(Color.Black.copy(alpha = 0.6f)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back button at top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Scanner icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(GreenPrimary, RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "Scan",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "Scan Parent QR Code",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Point your camera at the QR code\ndisplayed on the parent's device",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = GreenPrimary
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(GreenSurface, RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = "Camera",
                tint = GreenPrimary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Camera Permission Required",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = GreenPrimaryDark,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "To scan QR codes, please allow camera access",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
        ) {
            Text(
                text = "Grant Permission",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onBack) {
            Text(
                text = "Go Back",
                color = Color.Gray
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun QRScannerScreenPreview() {
    IPETheme {
        QRScannerScreen(
            onQRCodeScanned = {},
            onBack = {}
        )
    }
}
