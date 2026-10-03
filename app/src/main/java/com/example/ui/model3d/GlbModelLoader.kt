package com.example.ui.model3d

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Robust binary glTF (GLB) parser and loader for Android assets.
 * Reads binary GLB 2.0 chunk 0 (JSON) and chunk 1 (BIN), extracting meshes,
 * vertex positions, normals, and indices.
 */
object GlbModelLoader {

    fun loadGlbFromAsset(context: Context, assetPath: String): List<Polygon3D>? {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                parseGlbStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGlbStream(stream: InputStream): List<Polygon3D>? {
        val bytes = stream.readBytes()
        if (bytes.size < 20) return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // 12-byte GLB Header: magic (0x46546C67 "glTF"), version (2), length
        val magic = buffer.int
        if (magic != 0x46546C67) return null
        val version = buffer.int
        val totalLength = buffer.int

        // Chunk 0: JSON
        val jsonChunkLength = buffer.int
        val jsonChunkType = buffer.int
        if (jsonChunkType != 0x4E4F534A) return null // "JSON"

        val jsonBytes = ByteArray(jsonChunkLength)
        buffer.get(jsonBytes)
        val jsonStr = String(jsonBytes, Charsets.UTF_8)
        val gltf = JSONObject(jsonStr)

        // Chunk 1: BIN (optional)
        var binBytes = ByteArray(0)
        if (buffer.remaining() >= 8) {
            val binChunkLength = buffer.int
            val binChunkType = buffer.int
            if (binChunkType == 0x004E4942) { // "BIN\0"
                binBytes = ByteArray(binChunkLength)
                buffer.get(binBytes)
            }
        }

        return parseGltfMeshes(gltf, binBytes)
    }

    private fun parseGltfMeshes(gltf: JSONObject, binBytes: ByteArray): List<Polygon3D> {
        val polygons = mutableListOf<Polygon3D>()
        val binBuffer = ByteBuffer.wrap(binBytes).order(ByteOrder.LITTLE_ENDIAN)

        val accessors = gltf.optJSONArray("accessors") ?: return polygons
        val bufferViews = gltf.optJSONArray("bufferViews") ?: return polygons
        val meshes = gltf.optJSONArray("meshes") ?: return polygons

        fun readAccessorFloats(accessorIndex: Int): FloatArray {
            val accessor = accessors.getJSONObject(accessorIndex)
            val bufferViewIndex = accessor.getInt("bufferView")
            val bufferView = bufferViews.getJSONObject(bufferViewIndex)

            val byteOffset = bufferView.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val count = accessor.getInt("count")
            val type = accessor.getString("type")
            val componentsPerElement = when (type) {
                "VEC3" -> 3
                "VEC2" -> 2
                "SCALAR" -> 1
                "VEC4" -> 4
                else -> 3
            }

            val totalFloats = count * componentsPerElement
            val result = FloatArray(totalFloats)
            binBuffer.position(byteOffset)
            for (i in 0 until totalFloats) {
                if (binBuffer.remaining() >= 4) {
                    result[i] = binBuffer.float
                }
            }
            return result
        }

        fun readAccessorIndices(accessorIndex: Int): IntArray {
            val accessor = accessors.getJSONObject(accessorIndex)
            val bufferViewIndex = accessor.getInt("bufferView")
            val bufferView = bufferViews.getJSONObject(bufferViewIndex)

            val byteOffset = bufferView.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val count = accessor.getInt("count")
            val componentType = accessor.getInt("componentType")

            val result = IntArray(count)
            binBuffer.position(byteOffset)

            when (componentType) {
                5123 -> { // UNSIGNED_SHORT
                    for (i in 0 until count) {
                        result[i] = binBuffer.short.toInt() and 0xFFFF
                    }
                }
                5125 -> { // UNSIGNED_INT
                    for (i in 0 until count) {
                        result[i] = binBuffer.int
                    }
                }
                5121 -> { // UNSIGNED_BYTE
                    for (i in 0 until count) {
                        result[i] = binBuffer.get().toInt() and 0xFF
                    }
                }
                else -> {
                    for (i in 0 until count) {
                        result[i] = i
                    }
                }
            }
            return result
        }

        for (m in 0 until meshes.length()) {
            val meshObj = meshes.getJSONObject(m)
            val primitives = meshObj.optJSONArray("primitives") ?: continue

            for (p in 0 until primitives.length()) {
                val prim = primitives.getJSONObject(p)
                val attributes = prim.getJSONObject("attributes")
                val posAccessorIdx = attributes.optInt("POSITION", -1)
                if (posAccessorIdx == -1) continue

                val positions = readAccessorFloats(posAccessorIdx)
                val indicesIdx = prim.optInt("indices", -1)

                val indices = if (indicesIdx != -1) {
                    readAccessorIndices(indicesIdx)
                } else {
                    IntArray(positions.size / 3) { it }
                }

                val primColor = when (m % 5) {
                    0 -> Color(0xFFFFB300) // Kerala Auto Yellow
                    1 -> Color(0xFF263238) // Dark Chassis
                    2 -> Color(0xFF37474F) // Tires
                    3 -> Color(0xFF80DEEA) // Glass
                    else -> Color(0xFFCFD8DC) // Rims / metal
                }

                // Assemble triangles
                var i = 0
                while (i + 2 < indices.size) {
                    val i0 = indices[i] * 3
                    val i1 = indices[i + 1] * 3
                    val i2 = indices[i + 2] * 3

                    if (i0 + 2 < positions.size && i1 + 2 < positions.size && i2 + 2 < positions.size) {
                        val v0 = Vertex3D(positions[i0], positions[i0 + 1], positions[i0 + 2])
                        val v1 = Vertex3D(positions[i1], positions[i1 + 1], positions[i1 + 2])
                        val v2 = Vertex3D(positions[i2], positions[i2 + 1], positions[i2 + 2])

                        polygons.add(
                            Polygon3D(
                                vertices = listOf(v0, v1, v2),
                                color = primColor,
                                isDoubleSided = true
                            )
                        )
                    }
                    i += 3
                }
            }
        }

        return polygons
    }
}
