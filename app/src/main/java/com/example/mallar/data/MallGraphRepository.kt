package com.example.mallar.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

// ── Graph data structures ────────────────────────────────────────────────────

data class GraphNode(
    @SerializedName("id")     val id: Int,
    @SerializedName("x")      val x: Double,
    @SerializedName("y")      val y: Double,
    @SerializedName("shopId") val shopId: Int?
)

data class GraphEdge(
    @SerializedName("from")   val from: Int,
    @SerializedName("to")     val to: Int,
    @SerializedName("weight") val weight: Double
)

data class MallGraph(
    @SerializedName("nodes") val nodes: List<GraphNode>,
    @SerializedName("edges") val edges: List<GraphEdge>
)

// ── A* path result ───────────────────────────────────────────────────────────

data class AStarPath(
    val nodeIds: List<Int>,          // ordered node IDs on the path
    val totalDistancePx: Double,     // total path length in map pixels
    val steps: List<NavInstruction>  // turn-by-turn instructions
)

data class NavInstruction(
    val direction: AStarDirection,
    val distancePx: Double
)

enum class AStarDirection { STRAIGHT, LEFT, RIGHT, ARRIVED }

// ── Repository ───────────────────────────────────────────────────────────────

object MallGraphRepository {

    private var graph: MallGraph? = null

    fun load(context: Context): MallGraph {
        graph?.let { return it }
        val json = context.assets.open("mall_graph.json").bufferedReader().use { it.readText() }
        val loaded = Gson().fromJson(json, MallGraph::class.java)
        graph = loaded
        return loaded
    }

    // Returns the graph node that represents a given shopId (place ID)
    fun nodeForShop(graph: MallGraph, shopId: Int): GraphNode? =
        graph.nodes.firstOrNull { it.shopId == shopId }

    // ── A* algorithm ─────────────────────────────────────────────────────────

    fun aStar(graph: MallGraph, startShopId: Int, endShopId: Int): AStarPath? {
        val startNode = nodeForShop(graph, startShopId) ?: return null
        val endNode   = nodeForShop(graph, endShopId)   ?: return null
        return runAStar(graph, startNode.id, endNode.id)
    }

    private fun runAStar(graph: MallGraph, startId: Int, goalId: Int): AStarPath? {
        val nodeMap = graph.nodes.associateBy { it.id }
        val goal    = nodeMap[goalId] ?: return null

        // Build adjacency list
        val adj = mutableMapOf<Int, MutableList<Pair<Int, Double>>>()
        for (e in graph.edges) {
            adj.getOrPut(e.from) { mutableListOf() }.add(Pair(e.to, e.weight))
            adj.getOrPut(e.to)   { mutableListOf() }.add(Pair(e.from, e.weight))
        }

        // g-cost, f-cost, came-from
        val gCost = mutableMapOf<Int, Double>().withDefault { Double.MAX_VALUE }
        val fCost = mutableMapOf<Int, Double>().withDefault { Double.MAX_VALUE }
        val cameFrom = mutableMapOf<Int, Int>()

        gCost[startId] = 0.0
        fCost[startId] = heuristic(nodeMap[startId]!!, goal)

        // Priority queue ordered by f-cost
        val open = sortedSetOf(compareBy<Int> { fCost.getValue(it) }.thenBy { it })
        open.add(startId)

        while (open.isNotEmpty()) {
            val current = open.first()
            open.remove(current)

            if (current == goalId) {
                return reconstructPath(cameFrom, nodeMap, startId, goalId)
            }

            for ((neighbor, edgeWeight) in adj[current] ?: emptyList()) {
                val tentativeG = gCost.getValue(current) + edgeWeight
                if (tentativeG < gCost.getValue(neighbor)) {
                    cameFrom[neighbor] = current
                    gCost[neighbor] = tentativeG
                    fCost[neighbor] = tentativeG + heuristic(nodeMap[neighbor]!!, goal)
                    open.remove(neighbor)
                    open.add(neighbor)
                }
            }
        }
        return null // no path
    }

    private fun heuristic(a: GraphNode, b: GraphNode): Double {
        val dx = a.x - b.x; val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun reconstructPath(
        cameFrom: Map<Int, Int>,
        nodeMap: Map<Int, GraphNode>,
        startId: Int,
        goalId: Int
    ): AStarPath {
        val path = mutableListOf<Int>()
        var current = goalId
        while (current != startId) {
            path.add(current)
            current = cameFrom[current] ?: break
        }
        path.add(startId)
        path.reverse()

        // Compute total distance
        var total = 0.0
        for (i in 0 until path.size - 1) {
            val a = nodeMap[path[i]]!!; val b = nodeMap[path[i + 1]]!!
            val dx = a.x - b.x; val dy = a.y - b.y
            total += kotlin.math.sqrt(dx * dx + dy * dy)
        }

        // Build nav instructions (simple: compute angle changes)
        val instructions = buildInstructions(path, nodeMap)
        return AStarPath(path, total, instructions)
    }

    private fun buildInstructions(path: List<Int>, nodeMap: Map<Int, GraphNode>): List<NavInstruction> {
        if (path.size < 2) return emptyList()
        val result = mutableListOf<NavInstruction>()
        for (i in 0 until path.size - 1) {
            val a = nodeMap[path[i]]!!
            val b = nodeMap[path[i + 1]]!!
            val dx = b.x - a.x; val dy = b.y - a.y
            val segDist = kotlin.math.sqrt(dx * dx + dy * dy)

            val dir = if (i == 0) {
                AStarDirection.STRAIGHT
            } else {
                val prev = nodeMap[path[i - 1]]!!
                val angle = angleChange(prev, a, b)
                when {
                    angle > 30  -> AStarDirection.RIGHT
                    angle < -30 -> AStarDirection.LEFT
                    else        -> AStarDirection.STRAIGHT
                }
            }
            result.add(NavInstruction(dir, segDist))
        }
        result.add(NavInstruction(AStarDirection.ARRIVED, 0.0))
        return result
    }

    private fun angleChange(prev: GraphNode, cur: GraphNode, next: GraphNode): Double {
        val v1x = cur.x - prev.x; val v1y = cur.y - prev.y
        val v2x = next.x - cur.x; val v2y = next.y - cur.y
        val cross = v1x * v2y - v1y * v2x
        val dot   = v1x * v2x + v1y * v2y
        return Math.toDegrees(kotlin.math.atan2(cross, dot))
    }
}
