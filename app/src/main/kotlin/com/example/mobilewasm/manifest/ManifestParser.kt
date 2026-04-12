package com.example.mobilewasm.manifest

import org.json.JSONObject

/** Parses a `manifest.json` string into a [WasmManifest]. */
object ManifestParser {

    /**
     * @throws org.json.JSONException if the JSON is malformed or missing required fields.
     */
    fun parse(json: String): WasmManifest {
        val obj = JSONObject(json)

        val version     = obj.getInt("version")
        val name        = obj.getString("name")
        val description = obj.optString("description", "")

        val modulesArray = obj.getJSONArray("modules")
        val modules = (0 until modulesArray.length()).map { i ->
            val mod = modulesArray.getJSONObject(i)
            WasmModule(
                name        = mod.getString("name"),
                file        = mod.getString("file"),
                description = mod.optString("description", "")
            )
        }

        return WasmManifest(
            version     = version,
            name        = name,
            description = description,
            modules     = modules
        )
    }
}
