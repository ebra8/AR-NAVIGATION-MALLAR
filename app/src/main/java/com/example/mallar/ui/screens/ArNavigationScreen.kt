package com.example.mallar.ui.screens

import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.mallar.ar.ArCoordinateTransformer
import com.example.mallar.ar.configureArSessionForNavigation
import com.example.mallar.ar.ArrowSceneManager
import com.example.mallar.data.AStarDirection
import com.example.mallar.data.GraphNode
import com.google.ar.core.*
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ── Design tokens ─────────────────────────────────────────────────────────────
private val NavTeal     = Color(0xFF009688)
private val NavTealDark = Color(0xFF00695C)
private val NavGreen    = Color(0xFF43A047)

/** Rough pixel→metre scale for distance/time estimation */
private const val PX_TO_M   = 0.08f
private const val M_PER_MIN = 80f   // comfortable mall walking speed

// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ArNavigationScreen(onBackClick: () -> Unit) {

    // ── Path data ─────────────────────────────────────────────────────────────
    val pathNodes = remember {
        val path  = NavigationState.aStarPath ?: return@remember emptyList<GraphNode>()
        val graph = com.example.mallar.data.MallGraphRepository.loadedGraph
            ?: return@remember emptyList()
        path.nodeIds.mapNotNull { id -> graph.nodes.firstOrNull { it.id == id } }
    }
    val aStarPath       = remember { NavigationState.aStarPath }
    val destinationNode = pathNodes.lastOrNull()

    val distanceM = remember(aStarPath) {
        ((aStarPath?.totalDistancePx ?: 0.0) * PX_TO_M).roundToInt().coerceAtLeast(1)
    }
    val walkMinutes = remember(distanceM) {
        (distanceM / M_PER_MIN).coerceAtLeast(1f).roundToInt()
    }

    // ── UI state ──────────────────────────────────────────────────────────────
    var statusText          by remember { mutableStateOf("📷 Point at the floor and move slowly…") }
    var segmentIndex        by remember { mutableIntStateOf(0) }
    var showRouteSheet      by remember { mutableStateOf(false) }
    var showStoreCard       by remember { mutableStateOf(true) }
    var reachedNotification by remember { mutableStateOf<String?>(null) }
    val managerRef = remember { mutableStateOf<ArrowSceneManager?>(null) }

    // Auto-dismiss reached banner after 2.5 s
    LaunchedEffect(reachedNotification) {
        if (reachedNotification != null) {
            delay(2500)
            reachedNotification = null
        }
    }

    // ── Root layout ───────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        // ── AR view (full screen background) ──────────────────────────────────
        AndroidView(
            factory = { ctx ->
                ARSceneView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    planeRenderer.isEnabled = true
                    planeRenderer.isVisible = true

                    configureSession { session, config ->
                        configureArSessionForNavigation(session, config)
                    }

                    fun handleFrame(session: Session, frame: Frame) {
                        when (frame.camera.trackingState) {
                            TrackingState.TRACKING -> {
                                val manager = managerRef.value
                                if (manager == null) {
                                    val floorPlane = session
                                        .getAllTrackables(Plane::class.java)
                                        .firstOrNull {
                                            it.trackingState == TrackingState.TRACKING &&
                                                    it.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                                    it.extentX > 0.5f
                                        }
                                    if (floorPlane == null) {
                                        statusText = "👀 Scanning floor… move phone slowly"
                                        return
                                    }
                                    val hit = frame
                                        .hitTest(width / 2f, height / 2f)
                                        .firstOrNull { h ->
                                            val t = h.trackable
                                            t is Plane &&
                                                    t.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                                    t.isPoseInPolygon(h.hitPose)
                                        }
                                    if (hit == null) {
                                        statusText = "⚠️ Point camera center at the floor"
                                        return
                                    }
                                    if (pathNodes.size >= 2) {
                                        statusText = "✅ Floor found! Placing arrows…"
                                        val transformer = ArCoordinateTransformer(startNode = pathNodes.first())
                                        val newManager = ArrowSceneManager(
                                            sceneView   = this,
                                            transformer = transformer,
                                            pathNodes   = pathNodes
                                        )
                                        newManager.placeWorldOrigin(hit, frame)
                                        managerRef.value = newManager
                                        planeRenderer.isVisible = false
                                        statusText = "🎯 Follow the arrows!"
                                    } else {
                                        statusText = "❌ No navigation path loaded"
                                    }
                                } else {
                                    manager.onFrame(frame, segmentIndex) { reachedIdx ->
                                        val nodeName = pathNodes.getOrNull(reachedIdx)
                                            ?.shopName ?: "Node $reachedIdx"
                                        segmentIndex = reachedIdx
                                        reachedNotification = if (reachedIdx >= pathNodes.size - 1) {
                                            statusText = "🏁 You have arrived!"
                                            "🏁 Arrived at $nodeName!"
                                        } else {
                                            "✅ Reached: $nodeName"
                                        }
                                    }
                                }
                            }
                            TrackingState.PAUSED  -> statusText = "⚠️ Move slower — tracking lost"
                            TrackingState.STOPPED -> {
                                statusText = "❌ AR stopped. Please restart."
                                managerRef.value?.destroy()
                                managerRef.value = null
                            }
                        }
                    }
                    onSessionUpdated = { session, frame -> handleFrame(session, frame) }
                }
            },
            update = { /* AR session drives itself */ },
            modifier = Modifier.fillMaxSize()
        )

        // ── TOP: back button + target button + store card ─────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ← Back button (white circle)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // ⊙ Target button (teal circle)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(NavTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Location",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Store info card ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = showStoreCard && destinationNode != null,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit  = fadeOut(tween(300)) + shrinkVertically(tween(300))
            ) {
                if (destinationNode != null) {
                    StoreInfoCard(
                        node        = destinationNode,
                        distanceM   = distanceM,
                        walkMinutes = walkMinutes,
                        statusText  = statusText,
                        onClose     = { showStoreCard = false }
                    )
                }
            }
        }

        // ── CENTER: Waypoint reached notification ─────────────────────────────
        AnimatedVisibility(
            visible  = reachedNotification != null,
            modifier = Modifier.align(Alignment.Center),
            enter    = fadeIn() + scaleIn(),
            exit     = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = NavGreen.copy(alpha = 0.93f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Text(
                    text       = reachedNotification ?: "",
                    color      = Color.White,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── BOTTOM: action button + "Show Road →" ────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Teal direction / action button
            if (pathNodes.size >= 2) {
                val nextNode = pathNodes.getOrNull(segmentIndex + 1)
                val step     = aStarPath?.steps?.getOrNull(segmentIndex)
                val btnLabel = when (step?.direction) {
                    AStarDirection.LEFT    -> "↰  Turn Left"
                    AStarDirection.RIGHT   -> "↱  Turn Right"
                    AStarDirection.ARRIVED -> "🏁  You Arrived!"
                    else -> "▲  Head to ${nextNode?.shopName ?: "Next Stop"}"
                }
                Button(
                    onClick  = { /* informational */ },
                    shape    = RoundedCornerShape(24.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = NavTeal),
                    elevation = ButtonDefaults.buttonElevation(4.dp),
                    modifier = Modifier
                        .widthIn(min = 200.dp)
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(btnLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(14.dp))
            }

            // "Show Road →" tap row
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { showRouteSheet = !showRouteSheet }
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Road", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (showRouteSheet) Icons.Default.KeyboardArrowDown
                                  else Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ── BOTTOM SHEET: route detail ────────────────────────────────────────
        AnimatedVisibility(
            visible  = showRouteSheet,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter    = slideInVertically(tween(350)) { it },
            exit     = slideOutVertically(tween(300)) { it }
        ) {
            RouteSheet(
                pathNodes   = pathNodes,
                aStarPath   = aStarPath,
                segmentIndex = segmentIndex,
                distanceM   = distanceM,
                walkMinutes = walkMinutes,
                onEndNavigation = onBackClick
            )
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            managerRef.value?.destroy()
            managerRef.value = null
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Store info card  (top white card matching the mockup)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StoreInfoCard(
    node: GraphNode,
    distanceM: Int,
    walkMinutes: Int,
    statusText: String,
    onClose: () -> Unit
) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo — must use the android_asset URI scheme for Coil
            if (node.logo != null) {
                AsyncImage(
                    model             = "file:///android_asset/${node.logo}",
                    contentDescription = node.shopName,
                    modifier          = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale      = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NavTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Store, null, tint = Color.White)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text       = node.shopName ?: "Destination",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 17.sp,
                    color      = Color.Black
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn,  null, tint = Color(0xFFE53935), modifier = Modifier.size(13.dp))
                    Text(" ${distanceM}m",    fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Default.AccessTime,  null, tint = NavTeal,           modifier = Modifier.size(13.dp))
                    Text(" ${walkMinutes}min", fontSize = 13.sp, color = Color.Gray)
                }
                Text(statusText, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
            }

            // ✕ close
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Route sheet  (slide-up panel matching the right mockup screen)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RouteSheet(
    pathNodes: List<GraphNode>,
    aStarPath: com.example.mallar.data.AStarPath?,
    segmentIndex: Int,
    distanceM: Int,
    walkMinutes: Int,
    onEndNavigation: () -> Unit
) {
    val destination = pathNodes.lastOrNull()
    val steps       = remember(pathNodes, aStarPath) { buildStepItems(pathNodes, aStarPath) }

    Card(
        shape  = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // Drag handle
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFFDDDDDD), CircleShape)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))

            // Destination name
            Text(
                text       = destination?.shopName ?: "Destination",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.Black
            )
            Spacer(Modifier.height(4.dp))

            // Floor + walk time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("1st Floor", fontSize = 14.sp, color = Color.Gray)
                Text("  |  ${walkMinutes} min walk", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.weight(1f))
                Text("${distanceM}m", fontSize = 13.sp, color = NavTeal, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color(0xFFEEEEEE))
            Spacer(Modifier.height(16.dp))

            // Step list
            steps.forEachIndexed { i, step ->
                RouteStepRow(
                    step       = step,
                    isFirst    = i == 0,
                    isLast     = i == steps.size - 1,
                    isPassed   = step.nodeIndex != null && step.nodeIndex < segmentIndex,
                    isCurrent  = step.nodeIndex != null && step.nodeIndex == segmentIndex,
                    showLine   = i < steps.size - 1
                )
            }

            Spacer(Modifier.height(24.dp))

            // End Navigation button
            Button(
                onClick  = onEndNavigation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavTeal)
            ) {
                Text("End Navigation", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step row
// ─────────────────────────────────────────────────────────────────────────────
private data class StepItem(
    val label: String,
    val isWaypoint: Boolean,
    val nodeIndex: Int?          // non-null for waypoints
)

@Composable
private fun RouteStepRow(
    step: StepItem,
    isFirst: Boolean,
    isLast: Boolean,
    isPassed: Boolean,
    isCurrent: Boolean,
    showLine: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth()) {

        // Dot + vertical line column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            if (step.isWaypoint) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isFirst  -> NavGreen
                                isPassed -> NavTeal
                                isCurrent -> NavTeal
                                isLast   -> Color(0xFFBDBDBD)
                                else     -> Color(0xFFBDBDBD)
                            }
                        )
                )
            } else {
                // Instruction — small dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFCCCCCC))
                )
            }
            if (showLine) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(Color(0xFFDDDDDD))
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            if (step.isWaypoint && isFirst) {
                Text("Your Location", fontSize = 10.sp, color = NavGreen, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text       = step.label,
                fontSize   = if (step.isWaypoint) 15.sp else 13.sp,
                fontWeight = if (step.isWaypoint) FontWeight.SemiBold else FontWeight.Normal,
                color      = when {
                    isPassed   -> Color(0xFFAAAAAA)
                    isCurrent  -> NavTeal
                    isLast     -> Color(0xFF555555)
                    else       -> Color(0xFF333333)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Build step list: uses ONLY the REAL A* computed path.
//   - Named stores → waypoint rows (coloured dot)
//   - A* direction instructions → direction rows (small dot), interleaved correctly
// ─────────────────────────────────────────────────────────────────────────────
private fun buildStepItems(
    pathNodes: List<GraphNode>,
    aStarPath: com.example.mallar.data.AStarPath?
): List<StepItem> {
    val items        = mutableListOf<StepItem>()
    if (pathNodes.isEmpty()) return items

    val instructions = (aStarPath?.steps ?: emptyList())
        .filter { it.direction != AStarDirection.ARRIVED }

    // ── Always show start node ────────────────────────────────────────────────
    items += StepItem(
        label      = pathNodes[0].shopName ?: "Start",
        isWaypoint = true,
        nodeIndex  = 0
    )

    // ── Walk through every instruction in A* order ────────────────────────────
    // Each NavInstruction covers a subset of pathNodes starting at [nodeIndex].
    // We interleave any named stores that appear BEFORE the next instruction.
    instructions.forEachIndexed { instrIdx, instr ->
        val nextInstrNodeIdx = instructions.getOrNull(instrIdx + 1)?.nodeIndex
            ?: pathNodes.size - 1

        // Named stores that sit between this instruction's nodeIndex and the
        // start of the NEXT instruction (exclusive) — show them first
        for (ni in instr.nodeIndex + 1 until nextInstrNodeIdx) {
            val n = pathNodes.getOrNull(ni) ?: continue
            if (n.shopName != null) {
                items += StepItem(n.shopName, isWaypoint = true, nodeIndex = ni)
            }
        }

        // Direction step for this instruction
        val distM = (instr.distancePx * PX_TO_M).roundToInt().coerceAtLeast(1)
        val dirText = when (instr.direction) {
            AStarDirection.LEFT     -> "↰  Turn Left"
            AStarDirection.RIGHT    -> "↱  Turn Right"
            AStarDirection.STRAIGHT -> "↑  Go Straight  (~${distM}m)"
            else                    -> null
        }
        if (dirText != null) {
            items += StepItem(dirText, isWaypoint = false, nodeIndex = null)
        }
    }

    // Named stores after the last instruction (before destination)
    val lastInstrNodeIdx = instructions.lastOrNull()?.nodeIndex ?: 0
    for (ni in lastInstrNodeIdx + 1 until pathNodes.size - 1) {
        val n = pathNodes.getOrNull(ni) ?: continue
        if (n.shopName != null) {
            items += StepItem(n.shopName, isWaypoint = true, nodeIndex = ni)
        }
    }

    // ── Always show destination ───────────────────────────────────────────────
    if (pathNodes.size >= 2) {
        items += StepItem(
            label      = pathNodes.last().shopName ?: "Destination",
            isWaypoint = true,
            nodeIndex  = pathNodes.size - 1
        )
    }

    return items
}
