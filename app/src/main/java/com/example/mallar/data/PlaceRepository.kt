package com.example.mallar.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PlaceRepository {

    private var places: List<Place> = emptyList()

    fun load(context: Context): List<Place> {
        if (places.isNotEmpty()) return places
        return try {
            val json = context.assets.open("places.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Place>>() {}.type
            val loaded: List<Place> = Gson().fromJson(json, type)
            places = loaded
            loaded
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Returns the asset path for a logo, e.g. "logos/h&m.jpg" */
    fun logoAssetPath(place: Place): String = place.logo
}
