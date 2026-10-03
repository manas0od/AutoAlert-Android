package com.example.ui.model3d

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.imu.Quaternion
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance 3D Canvas Viewport for rendering the Auto-Rickshaw model
 * with real-time quaternion attitude tracking, lighting, depth sorting, ground grid,
 * and multi-touch orbital camera gestures.
 *
 * Vehicle Frame:
 *  +X = vehicle RIGHT
 *  +Y = vehicle UP
 *  +Z = vehicle FRONT
 *  -Z = vehicle REAR
 */
@Composable
fun AutoRickshaw3DViewport(
    orientation: Quaternion,
    cameraAzimuth: Float,
    cameraElevation: Float,
    cameraZoom: Float,
    isManualMode: Boolean,
    onOrbitChange: (azimuth: Float, elevation: Float) -> Unit,
    onZoomChange: (zoom: Float) -> Unit,
    modifier: Modifier = Modifier,
    modelPolygons: List<Polygon3D> = remember { AutoRickshawModel.createModel() }
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isManualMode) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // 1-finger / 2-finger pan controls camera azimuth and elevation
                    val newAzimuth = (cameraAzimuth - pan.x * 0.5f) % 360f
                    val newElevation = (cameraElevation - pan.y * 0.3f).coerceIn(-80f, 80f)
                    onOrbitChange(
                        if (newAzimuth < 0f) newAzimuth + 360f else newAzimuth,
                        newElevation
                    )

                    // Pinch zoom
                    if (zoom != 1.0f) {
                        val newZoom = (cameraZoom / zoom).coerceIn(1.6f, 6.0f)
                        onZoomChange(newZoom)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width * 0.5f
            val centerY = height * 0.52f
            val fovScale = width.coerceAtMost(height) * 1.05f

            // Compute camera position and basis vectors
            val azRad = Math.toRadians(cameraAzimuth.toDouble()).toFloat()
            val elRad = Math.toRadians(cameraElevation.toDouble()).toFloat()

            // Eye position on sphere
            val camX = cameraZoom * cos(elRad) * sin(azRad)
            val camY = cameraZoom * sin(elRad)
            val camZ = cameraZoom * cos(elRad) * cos(azRad)

            // Camera LookAt (target is origin 0,0,0)
            // Forward vector: Target - Eye = -Eye
            val fwdX = -camX
            val fwdY = -camY
            val fwdZ = -camZ
            val fwdLen = sqrt(fwdX * fwdX + fwdY * fwdY + fwdZ * fwdZ)
            val nFwdX = fwdX / fwdLen
            val nFwdY = fwdY / fwdLen
            val nFwdZ = fwdZ / fwdLen

            // World Up vector
            val upX = 0.0f
            val upY = 1.0f
            val upZ = 0.0f

            // Camera Right vector = Forward x Up
            var rX = nFwdY * upZ - nFwdZ * upY
            var rY = nFwdZ * upX - nFwdX * upZ
            var rZ = nFwdX * upY - nFwdY * upX
            val rLen = sqrt(rX * rX + rY * rY + rZ * rZ)
            if (rLen > 1e-6f) {
                rX /= rLen
                rY /= rLen
                rZ /= rLen
            } else {
                rX = 1.0f
                rY = 0.0f
                rZ = 0.0f
            }

            // True Camera Up vector = Right x Forward
            val uX = rY * nFwdZ - rZ * nFwdY
            val uY = rZ * nFwdX - rX * nFwdZ
            val uZ = rX * nFwdY - rY * nFwdX

            // 1. Draw 3D Ground Grid (Y = -0.55f)
            drawGroundGrid(
                centerX, centerY, fovScale,
                camX, camY, camZ,
                rX, rY, rZ,
                uX, uY, uZ,
                nFwdX, nFwdY, nFwdZ
            )

            // 2. Draw Ground Shadow under vehicle
            drawVehicleShadow(
                orientation,
                centerX, centerY, fovScale,
                camX, camY, camZ,
                rX, rY, rZ,
                uX, uY, uZ,
                nFwdX, nFwdY, nFwdZ
            )

            // 3. Transform & Sort Model Polygons
            // Light source: from top, slightly right and front in world space
            val lx = 0.35f
            val ly = 0.85f
            val lz = 0.40f
            val lLen = sqrt(lx * lx + ly * ly + lz * lz)
            val nLx = lx / lLen
            val nLy = ly / lLen
            val nLz = lz / lLen

            val renderedFaces = mutableListOf<RenderedFace>()

            for (poly in modelPolygons) {
                if (poly.vertices.size < 3) continue

                // Rotate vertices by vehicle orientation quaternion
                val worldVerts = Array(poly.vertices.size) { i ->
                    val v = poly.vertices[i]
                    val rot = orientation.rotateVector(v.x, v.y, v.z)
                    Vertex3D(rot[0], rot[1], rot[2])
                }

                // Calculate polygon normal in world space
                val v0 = worldVerts[0]
                val v1 = worldVerts[1]
                val v2 = worldVerts[2]

                val e1x = v1.x - v0.x
                val e1y = v1.y - v0.y
                val e1z = v1.z - v0.z

                val e2x = v2.x - v0.x
                val e2y = v2.y - v0.y
                val e2z = v2.z - v0.z

                var nx = e1y * e2z - e1z * e2y
                var ny = e1z * e2x - e1x * e2z
                var nz = e1x * e2y - e1y * e2x
                val nLen = sqrt(nx * nx + ny * ny + nz * nz)

                if (nLen > 1e-6f) {
                    nx /= nLen
                    ny /= nLen
                    nz /= nLen
                } else {
                    nx = 0f; ny = 1f; nz = 0f
                }

                // Project vertices into camera space and 2D screen coordinates
                var avgCamZ = 0.0f
                val screenPoints = Array(worldVerts.size) { i ->
                    val wv = worldVerts[i]
                    // Vector from camera eye to vertex
                    val dx = wv.x - camX
                    val dy = wv.y - camY
                    val dz = wv.z - camZ

                    // Camera space coords
                    val cx = dx * rX + dy * rY + dz * rZ
                    val cy = dx * uX + dy * uY + dz * uZ
                    val cz = dx * nFwdX + dy * nFwdY + dz * nFwdZ
                    avgCamZ += cz

                    // Perspective divide
                    val pz = if (cz > 0.05f) cz else 0.05f
                    val sx = centerX + (cx / pz) * fovScale
                    val sy = centerY - (cy / pz) * fovScale

                    Offset(sx, sy)
                }
                avgCamZ /= worldVerts.size

                // If entire polygon is behind camera, discard
                if (avgCamZ <= 0.1f) continue

                // Lambertian Lighting
                val dotL = (nx * nLx + ny * nLy + nz * nLz).coerceAtLeast(0.0f)
                val ambient = 0.45f
                val diffuse = 0.55f * dotL
                val intensity = (ambient + diffuse).coerceIn(0.25f, 1.0f)

                val shadedColor = Color(
                    red = (poly.color.red * intensity).coerceIn(0f, 1f),
                    green = (poly.color.green * intensity).coerceIn(0f, 1f),
                    blue = (poly.color.blue * intensity).coerceIn(0f, 1f),
                    alpha = poly.color.alpha
                )

                renderedFaces.add(
                    RenderedFace(
                        screenPoints = screenPoints,
                        shadedColor = shadedColor,
                        outlineColor = poly.color.copy(alpha = 0.35f),
                        depth = avgCamZ,
                        isTransparent = poly.isTransparent
                    )
                )
            }

            // Sort depth from farthest to nearest (Painter's algorithm)
            renderedFaces.sortByDescending { it.depth }

            // Render all sorted faces
            for (face in renderedFaces) {
                val path = Path().apply {
                    moveTo(face.screenPoints[0].x, face.screenPoints[0].y)
                    for (i in 1 until face.screenPoints.size) {
                        lineTo(face.screenPoints[i].x, face.screenPoints[i].y)
                    }
                    close()
                }

                // Fill polygon
                drawPath(path = path, color = face.shadedColor)

                // Draw edge highlight for crisp, technical definition
                if (!face.isTransparent) {
                    drawPath(
                        path = path,
                        color = face.outlineColor,
                        style = Stroke(width = 1.0f)
                    )
                }
            }

            // 4. Draw 3D Coordinate Axis Gizmo in corner
            drawCoordinateGizmo(
                orientation = orientation,
                viewportWidth = width,
                viewportHeight = height,
                camR = floatArrayOf(rX, rY, rZ),
                camU = floatArrayOf(uX, uY, uZ),
                camFwd = floatArrayOf(nFwdX, nFwdY, nFwdZ)
            )
        }
    }
}

private data class RenderedFace(
    val screenPoints: Array<Offset>,
    val shadedColor: Color,
    val outlineColor: Color,
    val depth: Float,
    val isTransparent: Boolean
)

private fun DrawScope.drawGroundGrid(
    centerX: Float, centerY: Float, fovScale: Float,
    camX: Float, camY: Float, camZ: Float,
    rX: Float, rY: Float, rZ: Float,
    uX: Float, uY: Float, uZ: Float,
    nFwdX: Float, nFwdY: Float, nFwdZ: Float
) {
    val groundY = -0.55f
    val gridHalfSize = 1.8f
    val steps = 8
    val stepSize = (gridHalfSize * 2f) / steps
    val gridColor = Color(0x33FFA000)

    fun project(wx: Float, wy: Float, wz: Float): Offset? {
        val dx = wx - camX
        val dy = wy - camY
        val dz = wz - camZ

        val cz = dx * nFwdX + dy * nFwdY + dz * nFwdZ
        if (cz <= 0.1f) return null

        val cx = dx * rX + dy * rY + dz * rZ
        val cy = dx * uX + dy * uY + dz * uZ

        val sx = centerX + (cx / cz) * fovScale
        val sy = centerY - (cy / cz) * fovScale
        return Offset(sx, sy)
    }

    for (i in 0..steps) {
        val offset = -gridHalfSize + i * stepSize
        // Line along Z
        val p1 = project(offset, groundY, -gridHalfSize)
        val p2 = project(offset, groundY, gridHalfSize)
        if (p1 != null && p2 != null) {
            drawLine(gridColor, p1, p2, strokeWidth = 1.0f)
        }

        // Line along X
        val p3 = project(-gridHalfSize, groundY, offset)
        val p4 = project(gridHalfSize, groundY, offset)
        if (p3 != null && p4 != null) {
            drawLine(gridColor, p3, p4, strokeWidth = 1.0f)
        }
    }
}

private fun DrawScope.drawVehicleShadow(
    orientation: Quaternion,
    centerX: Float, centerY: Float, fovScale: Float,
    camX: Float, camY: Float, camZ: Float,
    rX: Float, rY: Float, rZ: Float,
    uX: Float, uY: Float, uZ: Float,
    nFwdX: Float, nFwdY: Float, nFwdZ: Float
) {
    val groundY = -0.54f
    // If vehicle is inverted or tilted excessively, shadow fades out
    val tilt = orientation.getTiltAngleDegrees()
    val shadowAlpha = ((60f - tilt) / 60f).coerceIn(0.0f, 0.40f)
    if (shadowAlpha < 0.05f) return

    val shadowColor = Color.Black.copy(alpha = shadowAlpha)

    fun project(wx: Float, wy: Float, wz: Float): Offset? {
        val dx = wx - camX
        val dy = wy - camY
        val dz = wz - camZ
        val cz = dx * nFwdX + dy * nFwdY + dz * nFwdZ
        if (cz <= 0.1f) return null
        val cx = dx * rX + dy * rY + dz * rZ
        val cy = dx * uX + dy * uY + dz * uZ
        return Offset(centerX + (cx / cz) * fovScale, centerY - (cy / cz) * fovScale)
    }

    // Oval footprint on ground
    val segments = 12
    val rx = 0.58f
    val rz = 0.90f
    val path = Path()
    var first = true

    for (i in 0 until segments) {
        val theta = i * 2.0 * Math.PI / segments
        val p = project((sin(theta) * rx).toFloat(), groundY, (cos(theta) * rz).toFloat()) ?: continue
        if (first) {
            path.moveTo(p.x, p.y)
            first = false
        } else {
            path.lineTo(p.x, p.y)
        }
    }
    path.close()
    drawPath(path, color = shadowColor)
}

private fun DrawScope.drawCoordinateGizmo(
    orientation: Quaternion,
    viewportWidth: Float,
    viewportHeight: Float,
    camR: FloatArray,
    camU: FloatArray,
    camFwd: FloatArray
) {
    val gizmoCenterX = viewportWidth - 60f
    val gizmoCenterY = viewportHeight - 60f
    val axisLength = 34.0f

    // Transform vehicle unit axes by orientation
    val rightWorld = orientation.rotateVector(1f, 0f, 0f)  // +X
    val upWorld = orientation.rotateVector(0f, 1f, 0f)     // +Y
    val frontWorld = orientation.rotateVector(0f, 0f, 1f)  // +Z

    fun projectGizmo(wx: Float, wy: Float, wz: Float): Offset {
        val sx = wx * camR[0] + wy * camR[1] + wz * camR[2]
        val sy = wx * camU[0] + wy * camU[1] + wz * camU[2]
        return Offset(gizmoCenterX + sx * axisLength, gizmoCenterY - sy * axisLength)
    }

    val origin = Offset(gizmoCenterX, gizmoCenterY)
    val ptRight = projectGizmo(rightWorld[0], rightWorld[1], rightWorld[2])
    val ptUp = projectGizmo(upWorld[0], upWorld[1], upWorld[2])
    val ptFront = projectGizmo(frontWorld[0], frontWorld[1], frontWorld[2])

    // Background circle for gizmo
    drawCircle(
        color = Color(0xCC1A1A1A),
        radius = 42f,
        center = origin
    )

    // Red: +X Right
    drawLine(Color(0xFFE53935), origin, ptRight, strokeWidth = 3.5f)
    drawCircle(Color(0xFFE53935), radius = 4f, center = ptRight)

    // Green: +Y Up
    drawLine(Color(0xFF43A047), origin, ptUp, strokeWidth = 3.5f)
    drawCircle(Color(0xFF43A047), radius = 4f, center = ptUp)

    // Blue: +Z Front
    drawLine(Color(0xFF1E88E5), origin, ptFront, strokeWidth = 3.5f)
    drawCircle(Color(0xFF1E88E5), radius = 4f, center = ptFront)
}
