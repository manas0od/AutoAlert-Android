package com.example.ui.model3d

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

data class Vertex3D(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun plus(other: Vertex3D) = Vertex3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vertex3D) = Vertex3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vertex3D(x * scalar, y * scalar, z * scalar)
}

data class Polygon3D(
    val vertices: List<Vertex3D>,
    val color: Color,
    val isDoubleSided: Boolean = false,
    val isTransparent: Boolean = false
)

/**
 * Authentic 3D Kerala Auto-Rickshaw (Bajaj / Piaggio style TukTuk) model geometry.
 *
 * Vehicle Coordinate Frame:
 *   +X = vehicle RIGHT
 *   +Y = vehicle UP
 *   +Z = vehicle FRONT
 *   -Z = vehicle REAR
 */
object AutoRickshawModel {

    // Palette
    val YellowCanopy = Color(0xFFFFB300)
    val YellowCanopyDark = Color(0xFFFFA000)
    val AutoBlack = Color(0xFF212529)
    val AutoDarkGreen = Color(0xFF1B3B2B)
    val ChassisGray = Color(0xFF343A40)
    val TireDark = Color(0xFF2B303A)
    val RimSilver = Color(0xFFDEE2E6)
    val WindshieldGlass = Color(0x9980DEEA)
    val FrameBlack = Color(0xFF16191D)
    val HeadlightGlow = Color(0xFFFFF59D)
    val TaillightRed = Color(0xFFE53935)
    val SeatBrown = Color(0xFF4E342E)

    fun createModel(): List<Polygon3D> {
        val polygons = mutableListOf<Polygon3D>()

        // -------------------------------------------------------------
        // 1. CHASSIS / FLOOR BASE
        // -------------------------------------------------------------
        // Extends from Z = -0.85 (rear) to Z = +0.55 (front footwell), width X = -0.52 to +0.52, Y = -0.22 to -0.15
        val floorY = -0.20f
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.52f, floorY, -0.85f),
                    Vertex3D(0.52f, floorY, -0.85f),
                    Vertex3D(0.52f, floorY, 0.55f),
                    Vertex3D(-0.52f, floorY, 0.55f)
                ),
                color = ChassisGray,
                isDoubleSided = true
            )
        )

        // Front tapered footwell / nose floor
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.52f, floorY, 0.55f),
                    Vertex3D(0.52f, floorY, 0.55f),
                    Vertex3D(0.20f, floorY, 0.85f),
                    Vertex3D(-0.20f, floorY, 0.85f)
                ),
                color = ChassisGray,
                isDoubleSided = true
            )
        )

        // -------------------------------------------------------------
        // 2. LOWER CABIN PANELS (Dark Green / Black body with Yellow stripe)
        // -------------------------------------------------------------
        // Rear body wall
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.52f, floorY, -0.85f),
                    Vertex3D(0.52f, floorY, -0.85f),
                    Vertex3D(0.52f, 0.25f, -0.85f),
                    Vertex3D(-0.52f, 0.25f, -0.85f)
                ),
                color = AutoDarkGreen,
                isDoubleSided = true
            )
        )
        // Rear yellow accent band
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.53f, 0.15f, -0.852f),
                    Vertex3D(0.53f, 0.15f, -0.852f),
                    Vertex3D(0.53f, 0.22f, -0.852f),
                    Vertex3D(-0.53f, 0.22f, -0.852f)
                ),
                color = YellowCanopy,
                isDoubleSided = true
            )
        )

        // Left rear quarter panel (behind open door)
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.52f, floorY, -0.85f),
                    Vertex3D(-0.52f, floorY, -0.25f),
                    Vertex3D(-0.52f, 0.25f, -0.25f),
                    Vertex3D(-0.52f, 0.25f, -0.85f)
                ),
                color = AutoDarkGreen,
                isDoubleSided = true
            )
        )
        // Left yellow accent
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.522f, 0.15f, -0.85f),
                    Vertex3D(-0.522f, 0.15f, -0.25f),
                    Vertex3D(-0.522f, 0.22f, -0.25f),
                    Vertex3D(-0.522f, 0.22f, -0.85f)
                ),
                color = YellowCanopy,
                isDoubleSided = true
            )
        )

        // Right rear quarter panel (behind open door)
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(0.52f, floorY, -0.25f),
                    Vertex3D(0.52f, floorY, -0.85f),
                    Vertex3D(0.52f, 0.25f, -0.85f),
                    Vertex3D(0.52f, 0.25f, -0.25f)
                ),
                color = AutoDarkGreen,
                isDoubleSided = true
            )
        )
        // Right yellow accent
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(0.522f, 0.15f, -0.25f),
                    Vertex3D(0.522f, 0.15f, -0.85f),
                    Vertex3D(0.522f, 0.22f, -0.85f),
                    Vertex3D(0.522f, 0.22f, -0.25f)
                ),
                color = YellowCanopy,
                isDoubleSided = true
            )
        )

        // Front left cowl (in front of door opening)
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.52f, floorY, 0.35f),
                    Vertex3D(-0.52f, floorY, 0.55f),
                    Vertex3D(-0.50f, 0.22f, 0.55f),
                    Vertex3D(-0.52f, 0.22f, 0.35f)
                ),
                color = AutoDarkGreen,
                isDoubleSided = true
            )
        )
        // Front right cowl
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(0.52f, floorY, 0.55f),
                    Vertex3D(0.52f, floorY, 0.35f),
                    Vertex3D(0.52f, 0.22f, 0.35f),
                    Vertex3D(0.50f, 0.22f, 0.55f)
                ),
                color = AutoDarkGreen,
                isDoubleSided = true
            )
        )

        // -------------------------------------------------------------
        // 3. FRONT NOSE / APRON & HEADLIGHT
        // -------------------------------------------------------------
        // Angled front nose shield
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.50f, 0.22f, 0.55f),
                    Vertex3D(0.50f, 0.22f, 0.55f),
                    Vertex3D(0.20f, floorY + 0.05f, 0.88f),
                    Vertex3D(-0.20f, floorY + 0.05f, 0.88f)
                ),
                color = AutoDarkGreen,
                isDoubleSided = true
            )
        )
        // Center nose decorative yellow patch
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.16f, 0.08f, 0.75f),
                    Vertex3D(0.16f, 0.08f, 0.75f),
                    Vertex3D(0.12f, 0.20f, 0.60f),
                    Vertex3D(-0.12f, 0.20f, 0.60f)
                ),
                color = YellowCanopy,
                isDoubleSided = true
            )
        )

        // Front Headlight (Round octagonal disc facing +Z)
        val hlRadius = 0.09f
        val hlCenter = Vertex3D(0.0f, 0.10f, 0.82f)
        val hlSegments = 8
        val hlVerts = mutableListOf<Vertex3D>()
        for (i in 0 until hlSegments) {
            val angle = i * 2.0 * Math.PI / hlSegments
            hlVerts.add(
                Vertex3D(
                    hlCenter.x + (cos(angle) * hlRadius).toFloat(),
                    hlCenter.y + (sin(angle) * hlRadius).toFloat(),
                    hlCenter.z
                )
            )
        }
        polygons.add(Polygon3D(vertices = hlVerts, color = HeadlightGlow, isDoubleSided = true))

        // Rear Taillights (Left & Right Red rectangles)
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.46f, 0.02f, -0.855f),
                    Vertex3D(-0.34f, 0.02f, -0.855f),
                    Vertex3D(-0.34f, 0.12f, -0.855f),
                    Vertex3D(-0.46f, 0.12f, -0.855f)
                ),
                color = TaillightRed,
                isDoubleSided = true
            )
        )
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(0.34f, 0.02f, -0.855f),
                    Vertex3D(0.46f, 0.02f, -0.855f),
                    Vertex3D(0.46f, 0.12f, -0.855f),
                    Vertex3D(0.34f, 0.12f, -0.855f)
                ),
                color = TaillightRed,
                isDoubleSided = true
            )
        )

        // -------------------------------------------------------------
        // 4. WINDSHIELD & CABIN PILLARS
        // -------------------------------------------------------------
        // Glass pane (angled back from nose to canopy)
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.48f, 0.22f, 0.55f),
                    Vertex3D(0.48f, 0.22f, 0.55f),
                    Vertex3D(0.44f, 0.68f, 0.38f),
                    Vertex3D(-0.44f, 0.68f, 0.38f)
                ),
                color = WindshieldGlass,
                isDoubleSided = true,
                isTransparent = true
            )
        )
        // Windshield black border frame
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.50f, 0.20f, 0.55f),
                    Vertex3D(-0.45f, 0.20f, 0.55f),
                    Vertex3D(-0.41f, 0.70f, 0.38f),
                    Vertex3D(-0.46f, 0.70f, 0.38f)
                ),
                color = FrameBlack,
                isDoubleSided = true
            )
        )
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(0.45f, 0.20f, 0.55f),
                    Vertex3D(0.50f, 0.20f, 0.55f),
                    Vertex3D(0.46f, 0.70f, 0.38f),
                    Vertex3D(0.41f, 0.70f, 0.38f)
                ),
                color = FrameBlack,
                isDoubleSided = true
            )
        )

        // Rear roof support pillars (Left & Right)
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.52f, 0.25f, -0.85f),
                    Vertex3D(-0.48f, 0.25f, -0.85f),
                    Vertex3D(-0.44f, 0.68f, -0.82f),
                    Vertex3D(-0.48f, 0.68f, -0.82f)
                ),
                color = FrameBlack,
                isDoubleSided = true
            )
        )
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(0.48f, 0.25f, -0.85f),
                    Vertex3D(0.52f, 0.25f, -0.85f),
                    Vertex3D(0.48f, 0.68f, -0.82f),
                    Vertex3D(0.44f, 0.68f, -0.82f)
                ),
                color = FrameBlack,
                isDoubleSided = true
            )
        )

        // -------------------------------------------------------------
        // 5. SIGNATURE KERALA AUTO CANOPY / ROOF (Vibrant Yellow curved canopy)
        // -------------------------------------------------------------
        val roofTopY = 0.75f
        val roofSideY = 0.68f

        // Center main roof top panel
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.44f, roofSideY, 0.38f),
                    Vertex3D(0.44f, roofSideY, 0.38f),
                    Vertex3D(0.44f, roofSideY, -0.82f),
                    Vertex3D(-0.44f, roofSideY, -0.82f)
                ),
                color = YellowCanopy,
                isDoubleSided = true
            )
        )
        // Arched center ridge (creates slight canopy crown)
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.25f, roofTopY, 0.36f),
                    Vertex3D(0.25f, roofTopY, 0.36f),
                    Vertex3D(0.25f, roofTopY, -0.80f),
                    Vertex3D(-0.25f, roofTopY, -0.80f)
                ),
                color = YellowCanopyDark,
                isDoubleSided = true
            )
        )

        // Front canopy visor slope (slopes down over windshield)
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.46f, roofSideY - 0.04f, 0.42f),
                    Vertex3D(0.46f, roofSideY - 0.04f, 0.42f),
                    Vertex3D(0.44f, roofSideY, 0.38f),
                    Vertex3D(-0.44f, roofSideY, 0.38f)
                ),
                color = AutoBlack,
                isDoubleSided = true
            )
        )

        // Rear canopy overhang
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.46f, roofSideY - 0.03f, -0.86f),
                    Vertex3D(0.46f, roofSideY - 0.03f, -0.86f),
                    Vertex3D(0.44f, roofSideY, -0.82f),
                    Vertex3D(-0.44f, roofSideY, -0.82f)
                ),
                color = AutoBlack,
                isDoubleSided = true
            )
        )

        // Left canopy side valance / edge
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.44f, roofSideY, 0.38f),
                    Vertex3D(-0.44f, roofSideY, -0.82f),
                    Vertex3D(-0.45f, roofSideY - 0.05f, -0.82f),
                    Vertex3D(-0.45f, roofSideY - 0.05f, 0.38f)
                ),
                color = AutoBlack,
                isDoubleSided = true
            )
        )
        // Right canopy side valance
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(0.44f, roofSideY, -0.82f),
                    Vertex3D(0.44f, roofSideY, 0.38f),
                    Vertex3D(0.45f, roofSideY - 0.05f, 0.38f),
                    Vertex3D(0.45f, roofSideY - 0.05f, -0.82f)
                ),
                color = AutoBlack,
                isDoubleSided = true
            )
        )

        // -------------------------------------------------------------
        // 6. INTERIOR SEATING & CONTROLS
        // -------------------------------------------------------------
        // Rear passenger bench seat (Z = -0.55 to -0.80)
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.48f, -0.05f, -0.82f),
                    Vertex3D(0.48f, -0.05f, -0.82f),
                    Vertex3D(0.48f, -0.05f, -0.55f),
                    Vertex3D(-0.48f, -0.05f, -0.55f)
                ),
                color = SeatBrown,
                isDoubleSided = true
            )
        )
        // Rear seat backrest
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.48f, -0.05f, -0.82f),
                    Vertex3D(0.48f, -0.05f, -0.82f),
                    Vertex3D(0.48f, 0.20f, -0.82f),
                    Vertex3D(-0.48f, 0.20f, -0.82f)
                ),
                color = SeatBrown,
                isDoubleSided = true
            )
        )

        // Driver front seat cushion (Z = 0.0 to 0.28)
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.25f, -0.05f, 0.05f),
                    Vertex3D(0.25f, -0.05f, 0.05f),
                    Vertex3D(0.25f, -0.05f, 0.30f),
                    Vertex3D(-0.25f, -0.05f, 0.30f)
                ),
                color = SeatBrown,
                isDoubleSided = true
            )
        )

        // Handlebar cross bar (Z = 0.48, Y = 0.18)
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.22f, 0.17f, 0.48f),
                    Vertex3D(0.22f, 0.17f, 0.48f),
                    Vertex3D(0.22f, 0.19f, 0.48f),
                    Vertex3D(-0.22f, 0.19f, 0.48f)
                ),
                color = RimSilver,
                isDoubleSided = true
            )
        )

        // -------------------------------------------------------------
        // 7. WHEELS (1 Front Wheel, 2 Rear Wheels)
        // -------------------------------------------------------------
        // Front single wheel (Centered at X = 0, Y = -0.38, Z = 0.75)
        buildWheel(
            polygons = polygons,
            centerX = 0.0f,
            centerY = -0.38f,
            centerZ = 0.75f,
            radius = 0.17f,
            width = 0.08f,
            facingRight = true
        )

        // Front wheel mudguard / fork
        polygons.add(
            Polygon3D(
                vertices = listOf(
                    Vertex3D(-0.08f, -0.22f, 0.62f),
                    Vertex3D(0.08f, -0.22f, 0.62f),
                    Vertex3D(0.08f, -0.19f, 0.88f),
                    Vertex3D(-0.08f, -0.19f, 0.88f)
                ),
                color = AutoBlack,
                isDoubleSided = true
            )
        )

        // Left rear wheel (X = -0.54, Y = -0.38, Z = -0.52)
        buildWheel(
            polygons = polygons,
            centerX = -0.54f,
            centerY = -0.38f,
            centerZ = -0.52f,
            radius = 0.18f,
            width = 0.09f,
            facingRight = false
        )

        // Right rear wheel (X = +0.54, Y = -0.38, Z = -0.52)
        buildWheel(
            polygons = polygons,
            centerX = 0.54f,
            centerY = -0.38f,
            centerZ = -0.52f,
            radius = 0.18f,
            width = 0.09f,
            facingRight = true
        )

        return polygons
    }

    private fun buildWheel(
        polygons: MutableList<Polygon3D>,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        radius: Float,
        width: Float,
        facingRight: Boolean
    ) {
        val segments = 10
        val halfW = width * 0.5f
        val xInner = if (facingRight) centerX - halfW else centerX + halfW
        val xOuter = if (facingRight) centerX + halfW else centerX - halfW

        val outerRim = mutableListOf<Vertex3D>()
        val innerRim = mutableListOf<Vertex3D>()

        for (i in 0 until segments) {
            val angle = i * 2.0 * Math.PI / segments
            val dy = (sin(angle) * radius).toFloat()
            val dz = (cos(angle) * radius).toFloat()

            outerRim.add(Vertex3D(xOuter, centerY + dy, centerZ + dz))
            innerRim.add(Vertex3D(xInner, centerY + dy, centerZ + dz))
        }

        // Outer hubcap disc
        polygons.add(
            Polygon3D(
                vertices = outerRim,
                color = RimSilver,
                isDoubleSided = true
            )
        )

        // Inner hubcap disc
        polygons.add(
            Polygon3D(
                vertices = innerRim,
                color = TireDark,
                isDoubleSided = true
            )
        )

        // Tire tread tread band between inner and outer rims
        for (i in 0 until segments) {
            val nextI = (i + 1) % segments
            polygons.add(
                Polygon3D(
                    vertices = listOf(
                        innerRim[i],
                        outerRim[i],
                        outerRim[nextI],
                        innerRim[nextI]
                    ),
                    color = TireDark,
                    isDoubleSided = true
                )
            )
        }
    }
}
