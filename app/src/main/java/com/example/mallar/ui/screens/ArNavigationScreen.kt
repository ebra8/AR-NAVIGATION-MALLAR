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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.example.mallar.data.AStarDirection
import com.example.mallar.data.Place
import com.example.mallar.ui.theme.*
import kotlinx.coroutines.delay

// ── Navigation direction enum ─────────────────────────────────────────────────
enum class NavDirection { STRAIGHT, LEFT, RIGHT, ARRIVED }

data class NavStep(val direction: NavDirection, val distanceM: Int)

// ── AR Navigation Screen ───────────────────────────────────────────────────────
@Composable
fun ArNavigationScreen(onBackClick: () -> Unit) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val place          = NavigationState.selectedPlace

    // ── A* path state ─────────────────────────────────────────────────────────
    val aStarPath    = NavigationState.aStarPath
    val totalSteps   = aStarPath?.steps?.size ?: 0
    var currentStepIndex by remember { mutableIntStateOf(0) }

    // Current A* instruction mapped to NavStep
    val currentStep: NavStep = remember(currentStepIndex) {
        val path = aStarPath
        if (path != null && path.steps.isNotEmpty()) {
            val idx = currentStepIndex.coerceIn(0, path.steps.size - 1)
            val inst = path.steps[idx]
            // Convert map pixels to metres (empirical scale: ~0.25 m/px for typical mall map)
            val distM = (inst.distancePx * 0.25).toInt().coerceIn(1, 999)
            val dir = when (inst.direction) {
                AStarDirection.LEFT    -> NavDirection.LEFT
                AStarDirection.RIGHT   -> NavDirection.RIGHT
                AStarDirection.ARRIVED -> NavDirection.ARRIVED
                else                   -> NavDirection.STRAIGHT
            }
            NavStep(dir, distM)
        } else {
            NavStep(NavDirection.STRAIGHT, NavigationState.estimatedDistance.coerceAtLeast(10))
        }
    }

    // ── Automatic step advance ────────────────────────────────────────────────
    // Since we are INDOORS (no GPS), we advance steps automatically using a
    // time-based model: time = distance / average walking speed (1.2 m/s).
    // This gives realistic step durations without any button press.
    val isArrived = currentStep.direction == NavDirection.ARRIVED

    LaunchedEffect(currentStepIndex) {
        if (!isArrived && totalSteps > 0 && currentStepIndex < totalSteps - 1) {
            val stepPath = aStarPath!!.steps[currentStepIndex]
            val distM    = (stepPath.distancePx * 0.25).coerceAtLeast(1.0)
            val walkingSpeedMs = 1.2   // metres per second
            val durationMs = ((distM / walkingSpeedMs) * 1000).toLong()
                .coerceIn(1_500L, 15_000L)  // min 1.5s, max 15s per step
            delay(durationMs)
            currentStepIndex++
        }
    }

    // ── Step progress (fraction of current step elapsed) ─────────────────────
    var stepProgress by remember(currentStepIndex) { mutableFloatStateOf(0f) }
    LaunchedEffect(currentStepIndex) {
        if (!isArrived && totalSteps > 0 && currentStepIndex < totalSteps - 1) {
            val stepPath    = aStarPath!!.steps[currentStepIndex]
            val distM       = (stepPath.distancePx * 0.25).coerceAtLeast(1.0)
            val durationMs  = ((distM / 1.2) * 1000).toLong().coerceIn(1_500L, 15_000L)
            val startTime   = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                stepProgress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                if (elapsed >= durationMs) break
                delay(50)
            }
        }
    }

    // Animated step progress for smooth bar
    val animatedProgress by animateFloatAsState(
        targetValue = stepProgress,
        animationSpec = tween(100, easing = LinearEasing),
        label = "progress"
    )

    // ── Compass sensor ────────────────────────────────────────────────────────
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    var azimuthDeg by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val gravity      = FloatArray(3)
        val geomagnetic  = FloatArray(3)
        val R = FloatArray(9); val I = FloatArray(9)
        val orientation  = FloatArray(3)
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
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val animatedAzimuth by animateFloatAsState(
        targetValue = azimuthDeg,
        animationSpec = tween(200, easing = LinearEasing),
        label = "azimuth"
    )

    // ── Arrow animations ──────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val arrowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val arrowRotation = when (currentStep.direction) {
        NavDirection.LEFT     -> -45f
        NavDirection.RIGHT    -> 45f
        NavDirection.STRAIGHT -> 0f
        NavDirection.ARRIVED  -> 0f
    }
    val animatedArrowRot by animateFloatAsState(
        targetValue = arrowRotation,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "arrowRot"
    )

    // ── Overall trip progress (0..1) ──────────────────────────────────────────
    val overallProgress = if (totalSteps > 1)
        (currentStepIndex + animatedProgress) / (totalSteps - 1).toFloat()
    else if (isArrived) 1f else 0f

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        // Live camera preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                ProcessCameraProvider.getInstance(ctx).addListener({
                    try {
                        val cp = ProcessCameraProvider.getInstance(ctx).get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        cp.unbindAll()
                        cp.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                    } catch (e: Exception) {
                        android.util.Log.e("ArNav", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Semi-transparent overlay
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))

        // ── AR Arrows ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 230.dp, bottom = 190.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isArrived) {
                Canvas(
                    modifier = Modifier
                        .size(260.dp)
                        .rotate(animatedArrowRot)
                ) {
                    drawNavArrows(arrowPulse)
                }
            } else {
                // Arrived celebration
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = SuccessGreen.copy(alpha = 0.90f),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎉", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "You have arrived!",
                            color = White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                        place?.brand?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Welcome to $it", color = White.copy(alpha = 0.85f), fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        // ── Top bar ───────────────────────────────────────────────────────────
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
                color = White.copy(alpha = 0.92f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Teal)
                }
            }

            // Step counter badge
            if (totalSteps > 1 && !isArrived) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Teal.copy(alpha = 0.92f)
                ) {
                    Text(
                        "Step ${currentStepIndex + 1} / ${totalSteps - 1}",
                        color = White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            // Compass
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = White.copy(alpha = 0.92f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Explore,
                        "Compass",
                        tint = Teal,
                        modifier = Modifier.size(28.dp).rotate(-animatedAzimuth)
                    )
                }
            }
        }

        // ── Destination card ──────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 116.dp, start = 18.dp, end = 18.dp)
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = White
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceLight
                    ) {
                        if (place != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("file:///android_asset/${place.logo}")
                                    .crossfade(true).build(),
                                contentDescription = place.brand,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp)).padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            place?.brand ?: "Destination",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("${NavigationState.estimatedDistance}m", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(Icons.Filled.AccessTime, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("${NavigationState.estimatedMinutes}min", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        onClick = onBackClick,
                        modifier = Modifier.size(30.dp),
                        shape = CircleShape,
                        color = SurfaceLight
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, "Close", tint = TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Overall trip progress bar
                if (totalSteps > 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(SurfaceLight)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(overallProgress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(listOf(Teal, Color(0xFF00E676)))
                                )
                        )
                    }
                }
            }
        }

        // ── Bottom navigation pill ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val (pillLabel, pillIcon) = when (currentStep.direction) {
                NavDirection.LEFT     -> "Turn LEFT  ${currentStep.distanceM}m" to Icons.AutoMirrored.Filled.ArrowBack
                NavDirection.RIGHT    -> "Turn RIGHT  ${currentStep.distanceM}m" to Icons.AutoMirrored.Filled.ArrowForward
                NavDirection.STRAIGHT -> "Go STRAIGHT  ${currentStep.distanceM}m" to Icons.Filled.ArrowUpward
                NavDirection.ARRIVED  -> "You have ARRIVED!" to Icons.Filled.CheckCircle
            }
            val pillColor = if (isArrived) SuccessGreen else Teal

            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(74.dp)
                    .shadow(18.dp, RoundedCornerShape(37.dp),
                        ambientColor = pillColor, spotColor = pillColor),
                shape = RoundedCornerShape(37.dp),
                color = pillColor
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(pillIcon, null, tint = White, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(pillLabel, color = White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            // Auto-navigating indicator
            if (!isArrived && totalSteps > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotScale by infiniteTransition.animateFloat(
                        initialValue = 0.6f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                        label = "dot"
                    )
                    Box(
                        modifier = Modifier
                            .size((8 * dotScale).dp)
                            .background(Teal, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Auto-navigating",
                        color = White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── Canvas arrow drawing ───────────────────────────────────────────────────────
private fun DrawScope.drawNavArrows(pulseAlpha: Float) {
    val w = size.width; val h = size.height
    val cx = w / 2f;   val cy = h / 2f

    // 3 stacked arrows — bottom is brightest (leading)
    for (i in 0..2) {
        val offsetY = (i - 1) * (h * 0.25f)
        val alpha = when (i) {
            0 -> pulseAlpha * 0.28f
            1 -> pulseAlpha * 0.58f
            else -> pulseAlpha
        }
        drawArrow(cx, cy + offsetY, w * 0.40f, Color(0xFF00E676).copy(alpha = alpha), filled = (i == 2))
    }
    // Glow halo on leading arrow
    drawArrow(cx, cy - h * 0.25f, w * 0.42f, Color(0xFF69F0AE).copy(alpha = pulseAlpha * 0.18f), filled = false, strokeWidth = 20f)
}

private fun DrawScope.drawArrow(
    cx: Float, cy: Float,
    size: Float,
    color: Color,
    filled: Boolean,
    strokeWidth: Float = 9f
) {
    val halfW = size * 0.50f
    val bodyH = size * 0.52f
    val headH = size * 0.48f

    val path = Path().apply {
        moveTo(cx, cy - bodyH / 2f - headH)
        lineTo(cx - halfW, cy - bodyH / 2f)
        lineTo(cx - halfW * 0.40f, cy - bodyH / 2f)
        lineTo(cx - halfW * 0.40f, cy + bodyH / 2f)
        lineTo(cx + halfW * 0.40f, cy + bodyH / 2f)
        lineTo(cx + halfW * 0.40f, cy - bodyH / 2f)
        lineTo(cx + halfW, cy - bodyH / 2f)
        close()
    }

    if (filled) {
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                listOf(Color(0xFF00E676), Color(0xFF1DE9B6)),
                startY = cy - bodyH / 2f - headH,
                endY   = cy + bodyH / 2f
            )
        )
    } else {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}