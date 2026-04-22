package com.example.mallar.ar

import android.util.Log
import com.example.mallar.data.GraphNode
import com.google.ar.core.*
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import kotlin.math.sqrt

private const val TAG = "ArrowSceneManager"

// ── Arrow model settings ──────────────────────────────────────────────────────
// scaleToUnits = 0.4f → arrow ~40 cm. Increase if too small, decrease if too big.
// If arrow points wrong direction, change the +0f offset to +90f or +180f
private const val ARROW_MODEL_PATH  = "models/nav_arrow.glb"
private const val ARROW_SCALE       = 0.4f    // metres
private const val ARROW_FLOOR_Y     = 0.05f   // 5 cm above floor
private const val ARROW_ROT_OFFSET  = 0f      // change if GLB faces wrong direction

class ArrowSceneManager(
    private val sceneView: ARSceneView,
    private val transformer: ArCoordinateTransformer,
    private val pathNodes: List<GraphNode>
) {

    var rootAnchorNode: AnchorNode? = null
    var isWorldOriginSet = false
    var userArX = 0f
    var userArZ = 0f
    var worldRotationDeg: Float = 0f

    private data class ArrowEntry(
        val modelNode:   ModelNode,
        val placement:   ArrowPlacement,
        val targetArPos: ArPosition
    )

    private val arrows = mutableListOf<ArrowEntry>()

    private val nodeArPositions by lazy {
        pathNodes.map { transformer.toArLocal(it) }
    }

    // ─────────────────────────────────────────────────────────────────────────

    fun onFrame(frame: Frame, currentSegmentIdx: Int, onNodeReached: (Int) -> Unit) {
        if (frame.camera.trackingState != TrackingState.TRACKING) return
        updateUserPosition(frame.camera)
        if (isWorldOriginSet && currentSegmentIdx < pathNodes.size - 1) {
            val target = nodeArPositions.getOrNull(currentSegmentIdx + 1) ?: return
            if (transformer.distanceFromCamera(userArX, userArZ, target) < ARRIVAL_THRESHOLD_M) {
                onNodeReached(currentSegmentIdx + 1)
            }
        }
        if (isWorldOriginSet) updateArrowVisibility(currentSegmentIdx)
    }
    fun placeWorldOrigin(hitResult: HitResult, frame: Frame) {
        if (isWorldOriginSet) return

        val anchor = hitResult.createAnchor()
        val anchorNode = AnchorNode(sceneView.engine, anchor).also {
            sceneView.addChildNode(it)
        }

        rootAnchorNode = anchorNode
        isWorldOriginSet = true

        // 👇 الجديد هنا
        val pose = frame.camera.pose
        val zAxis = pose.zAxis  // forward direction
        worldRotationDeg = Math.toDegrees(
            kotlin.math.atan2(zAxis[0].toDouble(), zAxis[2].toDouble())
        ).toFloat()

        placeArrows(anchorNode)
    }
//    fun placeWorldOrigin(hitResult: HitResult) {
//        if (isWorldOriginSet) return
//        val anchor     = hitResult.createAnchor()
//        val anchorNode = AnchorNode(sceneView.engine, anchor).also { sceneView.addChildNode(it) }
//        rootAnchorNode = anchorNode
//        isWorldOriginSet = true
//        placeArrows(anchorNode)
//        Log.d(TAG, "World origin set. ${arrows.size} arrows placed.")
//    }

    fun destroy() {
        arrows.forEach { it.modelNode.destroy() }
        arrows.clear()
        rootAnchorNode?.destroy()
        rootAnchorNode = null
        isWorldOriginSet = false
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun updateUserPosition(camera: Camera) {
        val cam    = camera.pose
        val anchor = rootAnchorNode?.anchor?.pose
        if (anchor != null) {
            userArX = cam.tx() - anchor.tx()
            userArZ = cam.tz() - anchor.tz()
        } else {
            userArX = cam.tx()
            userArZ = cam.tz()
        }
    }

    private fun placeArrows(root: AnchorNode) {
        transformer.computeArrowPlacements(pathNodes).forEach { placement ->
            try {
                val p = placement.position
                val node = ModelNode(
                    modelInstance = sceneView.modelLoader.createModelInstance(
                        assetFileLocation = ARROW_MODEL_PATH
                    ),
                    scaleToUnits = ARROW_SCALE
                ).apply {

                    position = Position(p.x, ARROW_FLOOR_Y, p.z)

                    // 👇 اللون الأخضر
                    modelInstance.materialInstances.forEach { material ->
                        material.setParameter("baseColorFactor", 0f, 1f, 0f, 1f)
                    }

                    rotation = Rotation(
                        0f,
                        placement.yRotationDeg + worldRotationDeg + ARROW_ROT_OFFSET,
                        0f
                    )
                }
                root.addChildNode(node)
                arrows += ArrowEntry(node, placement, p)
                Log.d(TAG, "Arrow placed seg=${placement.segmentIndex} rot=${placement.yRotationDeg}deg")
            } catch (e: Exception) {
                Log.e(TAG, "Arrow load failed: ${e.message}")
            }
        }
    }

    private fun updateArrowVisibility(currentSegmentIdx: Int) {
        arrows.forEach { entry ->
            entry.modelNode.isVisible = entry.placement.segmentIndex >= currentSegmentIdx
        }
    }

    companion object {
        const val ARRIVAL_THRESHOLD_M = 1.2f
    }
}
