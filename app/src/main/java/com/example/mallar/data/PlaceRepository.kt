package com.example.mallar.data

import android.content.Context
import com.google.gson.Gson

object PlaceRepository {

    private var places: List<Place> = emptyList()

    fun load(context: Context): List<Place> {
        if (places.isNotEmpty()) return places
        return try {
            val json = context.assets.open("mall_graph.json").bufferedReader().use { it.readText() }
            val graph = Gson().fromJson(json, com.example.mallar.data.MallGraph::class.java)
            val loaded = graph.nodes
                .filter { it.shopId != null && it.shopName != null }
                .map { node ->
                    Place(
                        id    = node.shopId!!,
                        brand = node.shopName!!,
                        x     = node.x.toInt(),
                        y     = node.y.toInt(),
                        logo  = node.logo ?: ""
                    )
                }
            places = loaded
            loaded
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun logoAssetPath(place: Place): String = place.logo
}