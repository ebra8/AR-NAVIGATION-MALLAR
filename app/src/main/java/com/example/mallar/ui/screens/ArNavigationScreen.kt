package com.example.mallar.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mallar.data.Place
import com.example.mallar.ui.theme.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

// ── Navigation direction from A* ──────────────────────────────────────────────
enum class NavDirection { STRAIGHT, LEFT, RIGHT, ARRIVED }

data class NavStep(val direction: NavDirection, val distanceM: Int)

// ── Real A* step from stored path ─────────────────────────────────────────────
/**
 * Returns the current NavStep using the A* path computed in LogoScanScreen.
 * - We walk through the A* instruction list and return the first unfinished step.
 * - If no A* path is available, fall back to compass-based approximation.
 */
fun computeNextStep(place: Place, currentHeadingDeg: Float, stepIndex: Int): NavStep {
    val path = NavigationState.aStarPath

    // ── Use real A* instructions ────────────────────────────────────────────
    if (path != null && path.steps.isNotEmpty()) {
        val idx = stepIndex.coerceIn(0, path.steps.size - 1)
        val instruction = path.steps[idx]
        val distPx = instruction.distancePx
        // Convert map-pixel distance to approximate meters (scale factor ~0.3m/px for a typical mall map)
        val distM = (distPx * 0.3).toInt().coerceIn(1, 999)

        val dir = when (instruction.direction) {
            com.example.mallar.data.AStarDirection.LEFT    -> NavDirection.LEFT
            com.example.mallar.data.AStarDirection.RIGHT   -> NavDirection.RIGHT
            com.example.mallar.data.AStarDirection.ARRIVED -> NavDirection.ARRIVED
            else                                           -> NavDirection.STRAIGHT
        }
        return NavStep(dir, distM)
    }

    // ── Fallback: compass-based approximate direction ───────────────────────
    val mapCenterX = 450f; val mapCenterY = 350f
    val dx = place.x.toFloat() - mapCenterX
    val dy = -(place.y.toFloat() - mapCenterY)
    val targetBearing = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
        .let { if (it < 0) it + 360f else it }
    var diff = targetBearing - currentHeadingDeg
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    val distance = sqrt((place.x - 319f) * (place.x - 319f) + (place.y - 227f) * (place.y - 227f))
        .toInt().coerceIn(30, 500)
    return when {
        distance < 20   -> NavStep(NavDirection.ARRIVED, 0)
        abs(diff) < 25f -> NavStep(NavDirection.STRAIGHT, distance)
        diff > 0f       -> NavStep(NavDirection.RIGHT, distance)
        else            -> NavStep(NavDirection.LEFT, distance)
    }
}

@Composable
fun ArNavigationScreen(
    onBackClick: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val place          = NavigationState.selectedPlace

    // ── Compass sensor ────────────────────────────────────────────────────────
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    var azimuthDeg by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val gravity    = FloatArray(3)
        val geomagnetic = FloatArray(3)
        val R = FloatArray(9)
        val I = FloatArray(9)
        val orientation = FloatArray(3)
        val alpha = 0.97f

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomagnetic[0] = alpha * geomagnetic[0] + (1 - alpha) * event.values[0]
                        geomagnetic[1] = alpha * geomagnetic[1] + (1 - alpha) * event.values[1]
                        geomagnetic[2] = alpha * geomagnetic[2] + (1 - alpha) * event.values[2]
                    }
                }
                if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                    SensorManager.getOrientation(R, orientation)
                    azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        .let { if (it < 0) it + 360f else it }
                }
            }
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, magnetometer,  SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Smooth azimuth animation
    val animatedAzimuth by animateFloatAsState(
        targetValue = azimuthDeg,
        animationSpec = tween(200, easing = LinearEasing),
        label = "azimuth"
    )

    // ── A* step tracking ──────────────────────────────────────────────────────
    val totalSteps = NavigationState.aStarPath?.steps?.size ?: 0
    var currentStepIndex by remember { mutableIntStateOf(0) }

    // Current nav step (from real A* path or compass fallback)
    val step = remember(animatedAzimuth, place, currentStepIndex) {
        if (place != null) computeNextStep(place, animatedAzimuth, currentStepIndex)
        else NavStep(NavDirection.STRAIGHT, 200)
    }

    // Auto-advance to next step when distance becomes very small
    LaunchedEffect(step.distanceM) {
        if (step.distanceM < 15 && step.direction != NavDirection.ARRIVED && currentStepIndex < totalSteps - 1) {
            kotlinx.coroutines.delay(1500)
            currentStepIndex++
        }
    }

    // Arrow pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "arrow_pulse")
    val arrowPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // Arrow rotation based on A* direction
    val arrowRotation = when (step.direction) {
        NavDirection.LEFT    -> -45f
        NavDirection.RIGHT   -> 45f
        NavDirection.STRAIGHT -> 0f
        NavDirection.ARRIVED -> 0f
    }
    val animatedArrowRot by animateFloatAsState(
        targetValue = arrowRotation,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "arrowRot"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Live Camera Preview ───────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("ArNav", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Semi-transparent overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.15f))
        )

        // ── AR Navigation Arrows (Canvas) ─────────────────────────────────────
        if (step.direction != NavDirection.ARRIVED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 220.dp, bottom = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(260.dp)
                        .rotate(animatedArrowRot)
                ) {
                    drawNavArrows(arrowPulse)
                }
            }
        } else {
            // Arrived state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 220.dp, bottom = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SuccessGreen.copy(alpha = 0.85f),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "✅ You have arrived!",
                        color = White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
            }
        }

        // ── Top Bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBackClick,
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = White.copy(alpha = 0.9f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Teal)
                }
            }

            // Compass indicator (rotates with phone)
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = White.copy(alpha = 0.9f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Explore,
                        contentDescription = "Compass",
                        tint = Teal,
                        modifier = Modifier
                            .size(28.dp)
                            .rotate(-animatedAzimuth) // Needle stays pointing north
                    )
                }
            }
        }

        // ── Destination Card ──────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 116.dp, start = 18.dp, end = 18.dp)
                .fillMaxWidth()
                .height(100.dp)
                .shadow(20.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand logo
                Surface(
                    modifier = Modifier.size(70.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceLight
                ) {
                    if (place != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/${place.logo}")
                                .crossfade(true)
                                .build(),
                            contentDescription = place.brand,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp))
                                .padding(8.dp)
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("?", fontWeight = FontWeight.ExtraBold, color = Teal, fontSize = 22.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place?.brand ?: "Destination",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "${NavigationState.estimatedDistance}m",
                            color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(Icons.Filled.AccessTime, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "${NavigationState.estimatedMinutes}min",
                            color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    onClick = onBackClick,
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = SurfaceLight
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // ── Bottom Navigation Pill ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val pillLabel = when (step.direction) {
                NavDirection.LEFT    -> "◀ LEFT: ${step.distanceM}m"
                NavDirection.RIGHT   -> "RIGHT: ${step.distanceM}m ▶"
                NavDirection.STRAIGHT -> "▲ STRAIGHT: ${step.distanceM}m"
                NavDirection.ARRIVED -> "✅ ARRIVED"
            }
            val pillColor = when (step.direction) {
                NavDirection.ARRIVED -> SuccessGreen
                else -> Teal
            }

            Surface(
                modifier = Modifier
                    .width(280.dp)
                    .height(72.dp)
                    .shadow(16.dp, RoundedCornerShape(36.dp),
                        ambientColor = pillColor, spotColor = pillColor),
                shape = RoundedCornerShape(36.dp),
                color = pillColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = pillLabel,
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Show Road",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ── Custom GPS-style arrow drawn on Canvas ────────────────────────────────────
private fun DrawScope.drawNavArrows(pulseAlpha: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f

    // Draw 3 stacked arrows (like GPS turn-by-turn)
    val arrowColor = Color(0xFF00E676) // bright green
    val arrowGlow  = Color(0xFF69F0AE) // lighter for glow

    for (i in 0..2) {
        val offset = (i - 1) * (h * 0.25f)
        val alpha = when (i) {
            0 -> pulseAlpha * 0.35f
            1 -> pulseAlpha * 0.65f
            2 -> pulseAlpha * 1f
            else -> pulseAlpha
        }
        drawArrow(cx, cy + offset, w * 0.38f, arrowColor.copy(alpha = alpha), i == 2)
    }

    // Glow on the leading arrow
    drawArrow(cx, cy - h * 0.25f, w * 0.38f, arrowGlow.copy(alpha = pulseAlpha * 0.25f), false, strokeWidth = 18f)
}

private fun DrawScope.drawArrow(
    cx: Float, cy: Float,
    size: Float,
    color: Color,
    filled: Boolean,
    strokeWidth: Float = 10f
) {
    val halfW = size * 0.5f
    val bodyH  = size * 0.55f
    val headH  = size * 0.45f

    // Arrow shape path (pointing up)
    val path = Path().apply {
        // Arrow head (triangle pointing up)
        moveTo(cx, cy - bodyH / 2f - headH)
        lineTo(cx - halfW, cy - bodyH / 2f)
        lineTo(cx - halfW * 0.42f, cy - bodyH / 2f)
        lineTo(cx - halfW * 0.42f, cy + bodyH / 2f)
        lineTo(cx + halfW * 0.42f, cy + bodyH / 2f)
        lineTo(cx + halfW * 0.42f, cy - bodyH / 2f)
        lineTo(cx + halfW, cy - bodyH / 2f)
        close()
    }

    if (filled) {
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                listOf(Color(0xFF00E676), Color(0xFF1DE9B6)),
                startY = cy - bodyH / 2f - headH,
                endY = cy + bodyH / 2f
            )
        )
    } else {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth)
        )
    }
}
