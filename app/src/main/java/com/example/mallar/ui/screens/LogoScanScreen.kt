package com.example.mallar.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mallar.ml.LogoDetector
import com.example.mallar.ui.theme.*
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class ScanState { IDLE, SCANNING, FOUND, NOT_FOUND }

@Composable
fun LogoScanScreen(
    onBackClick: () -> Unit,
    onStoreSelected: () -> Unit
) {
    var isExpanded     by remember { mutableStateOf(false) }
    var searchQuery    by remember { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context        = LocalContext.current

    val logoDetector   = remember { LogoDetector(context) }
    var scanState      by remember { mutableStateOf(ScanState.IDLE) }
    var detectedBrand  by remember { mutableStateOf<String?>(null) }
    var detectedScore  by remember { mutableStateOf(0f) }

    val latestBitmap  = remember { AtomicReference<Bitmap?>(null) }
    val scanRequested = remember { AtomicBoolean(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Camera Preview ────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
                    ProcessCameraProvider.getInstance(ctx)
                val executor = Executors.newSingleThreadExecutor()

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            val bmp = imageProxy.toBitmapSafe()
                            if (bmp != null) {
                                latestBitmap.set(bmp)
                                if (scanRequested.getAndSet(false)) {
                                    val result = logoDetector.detect(bmp)
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        if (result != null) {
                                            detectedBrand = result.brand
                                            detectedScore = result.similarity
                                            scanState = ScanState.FOUND
                                        } else {
                                            scanState = ScanState.NOT_FOUND
                                        }
                                    }
                                }
                            }
                            imageProxy.close()
                        }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("LogoScanScreen", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Scan frame overlay ────────────────────────────────────────────────
        if (!isExpanded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val infiniteTransition = rememberInfiniteTransition(label = "scan")
                val scanAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f, targetValue = 0.9f,
                    animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
                    label = "alpha"
                )
                val frameColor = when (scanState) {
                    ScanState.SCANNING  -> Color.Yellow
                    ScanState.FOUND     -> Color.Green
                    ScanState.NOT_FOUND -> Color.Red
                    ScanState.IDLE      -> White
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .border(
                                width = if (scanState == ScanState.IDLE) 2.dp else 3.dp,
                                color = frameColor.copy(alpha = if (scanState == ScanState.IDLE) scanAlpha else 1f),
                                shape = RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (scanState == ScanState.SCANNING) {
                            CircularProgressIndicator(color = Color.Yellow, modifier = Modifier.size(48.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    val labelText = when (scanState) {
                        ScanState.IDLE      -> "Point camera at a store logo"
                        ScanState.SCANNING  -> "Scanning…"
                        ScanState.FOUND     -> "✅ Logo recognised!"
                        ScanState.NOT_FOUND -> "❌ No logo found — try again"
                    }
                    Text(
                        text = labelText,
                        color = frameColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    if (scanState == ScanState.NOT_FOUND) {
                        LaunchedEffect(scanState) {
                            kotlinx.coroutines.delay(2000)
                            scanState = ScanState.IDLE
                        }
                    }
                }
            }
        }

        // ── Back button ───────────────────────────────────────────────────────
        AnimatedVisibility(visible = !isExpanded, enter = fadeIn(), exit = fadeOut()) {
            Surface(
                onClick = onBackClick,
                modifier = Modifier.statusBarsPadding().padding(16.dp).size(56.dp),
                shape = CircleShape, color = White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Teal, modifier = Modifier.size(24.dp))
                }
            }
        }

        // ── Result sheet ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isExpanded || scanState == ScanState.FOUND,
            enter = slideInVertically { it } + fadeIn(),
            exit  = slideOutVertically { it } + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 100.dp)
                    .background(Color.White.copy(alpha = 0.97f), RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                val displayList = if (detectedBrand != null) {
                    listOf(StoreSearchItem(
                        name = detectedBrand!!,
                        initials = detectedBrand!!.take(2).uppercase(),
                        distance = "Nearby",
                        time = "${"%.0f".format(detectedScore * 100)}% match",
                        themeColor = Teal
                    ))
                } else {
                    sampleStoreList
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    items(displayList) { store ->
                        StoreResultItem(store, onClick = {
                            detectedBrand = null
                            scanState = ScanState.IDLE
                            onStoreSelected()
                        })
                    }
                }
            }
        }

        // ── Bottom bar ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (isExpanded) 16.dp else 0.dp, bottom = if (isExpanded) 0.dp else 32.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = if (isExpanded) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search field
                Surface(
                    modifier = Modifier.weight(1f).height(64.dp).shadow(10.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp), color = White
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFC7B8F5), RoundedCornerShape(12.dp))
                                .clickable { isExpanded = true }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty() && !isExpanded) {
                                Text("Search for stores...", color = TextSecondary.copy(alpha = 0.5f), fontSize = 14.sp)
                            } else {
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = TextPrimary),
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                            }
                        }
                        if (isExpanded) {
                            IconButton(onClick = { isExpanded = false; searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = TextSecondary)
                            }
                        }
                    }
                }

                if (!isExpanded) {
                    Spacer(modifier = Modifier.width(10.dp))
                    // SCAN button
                    Button(
                        onClick = {
                            if (scanState != ScanState.SCANNING) {
                                scanState = ScanState.SCANNING
                                detectedBrand = null
                                scanRequested.set(true)
                            }
                        },
                        modifier = Modifier.height(64.dp).width(96.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (scanState == ScanState.SCANNING) Color.Gray else Teal
                        ),
                        enabled = scanState != ScanState.SCANNING
                    ) {
                        Text(
                            text = if (scanState == ScanState.SCANNING) "…" else "Scan",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp, color = White
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        modifier = Modifier.size(56.dp), shape = CircleShape, color = White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Teal.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Tune, null, tint = Teal)
                        }
                    }
                }
            }
        }
    }
}

// ── Store result row ──────────────────────────────────────────────────────────

@Composable
private fun StoreResultItem(store: StoreSearchItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(80.dp), shape = CircleShape, color = SurfaceLight, shadowElevation = 2.dp) {
            Box(contentAlignment = Alignment.Center) {
                Text(store.initials, fontWeight = FontWeight.ExtraBold, color = store.themeColor, fontSize = 20.sp)
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column {
            Text(store.name, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = RedAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(store.distance, color = TextSecondary, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Icon(Icons.Default.AccessTime, null, tint = RedAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(store.time, color = TextSecondary, fontSize = 15.sp)
            }
        }
    }
}

private data class StoreSearchItem(
    val name: String, val initials: String,
    val distance: String, val time: String,
    val themeColor: Color
)

private val sampleStoreList = listOf(
    StoreSearchItem("Zara",        "ZA", "230m", "5min", Color(0xFF1A1A1A)),
    StoreSearchItem("MANGO",       "MA", "240m", "5min", Color(0xFFD4A34F)),
    StoreSearchItem("Bershka",     "BE", "250m", "6min", Color(0xFF167D92)),
    StoreSearchItem("PUMA",        "PU", "290m", "8min", Color(0xFF1C3A5F)),
    StoreSearchItem("PULL & BEAR", "PB", "293m", "8min", Color(0xFF0096D6))
)

// ── YUV → Bitmap ──────────────────────────────────────────────────────────────

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun ImageProxy.toBitmapSafe(): Bitmap? {
    val image = this.image ?: return null
    if (image.format != ImageFormat.YUV_420_888) {
        return try { this.toBitmap() } catch (e: Exception) { null }
    }
    val yBuf = image.planes[0].buffer
    val uBuf = image.planes[1].buffer
    val vBuf = image.planes[2].buffer
    val nv21 = ByteArray(yBuf.remaining() + uBuf.remaining() + vBuf.remaining())
    yBuf.get(nv21, 0, yBuf.remaining())
    vBuf.get(nv21, yBuf.capacity(), vBuf.remaining())
    uBuf.get(nv21, yBuf.capacity() + vBuf.capacity(), uBuf.remaining())
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), 95, out)
    val bytes = out.toByteArray()
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val mat = android.graphics.Matrix().also { it.postRotate(imageInfo.rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true)
}