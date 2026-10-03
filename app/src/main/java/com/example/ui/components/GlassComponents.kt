package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AutoAmberContainer
import com.example.ui.theme.AutoAmberPrimary
import com.example.ui.theme.AutoBackground
import com.example.ui.theme.AutoGlassBackground
import com.example.ui.theme.AutoGlassBorder
import com.example.ui.theme.AutoGlassHighlight
import com.example.ui.theme.AutoOnSurface
import com.example.ui.theme.AutoOnSurfaceVariant
import com.example.ui.theme.AutoStatusCancelled
import com.example.ui.theme.AutoStatusCompleted
import com.example.ui.theme.AutoStatusLowBattery
import com.example.ui.theme.AutoStatusPending
import com.example.ui.theme.AutoSurfaceHigh
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.hazeEffect
import com.example.util.LocalStrings

/**
 * Ambient CompositionLocal holding the active HazeState for blur coordination across layers.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Standard 1.dp vertical gradient brush for glassmorphic borders:
 * White (40% opacity) fading to White (5% opacity) to mimic light catching a curved glass edge.
 */
val GlassBorderGradient = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.40f),
        Color.White.copy(alpha = 0.05f)
    )
)

/**
 * Glassmorphic modifier extension adhering to the exact spec:
 * 1. Shadow: elevation = 8.dp with corner radius shape
 * 2. Blur: Haze blur child/effect layer behind content
 * 3. Translucent surface: 10-15% opacity white/color tint
 * 4. Border: 1.dp vertical gradient brush (white 40% -> 5%)
 */
fun Modifier.glassmorphic(
    hazeState: HazeState? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    elevation: Dp = 8.dp,
    tintColor: Color = Color.White.copy(alpha = 0.12f),
    borderBrush: Brush = GlassBorderGradient,
    borderWidth: Dp = 1.dp
): Modifier {
    var mod = this
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)

    if (hazeState != null) {
        mod = mod.hazeEffect(
            state = hazeState,
            style = HazeDefaults.style(
                backgroundColor = tintColor,
                tint = HazeTint(tintColor),
                blurRadius = 20.dp
            )
        )
    } else {
        mod = mod.background(tintColor)
    }

    return mod.border(width = borderWidth, brush = borderBrush, shape = shape)
}

/**
 * GlassBackgroundCanvas renders a rich ambient backdrop for the AutoAlert dark glass design language.
 * Features glowing ambient amber radial orbs, soft emerald accents, dark translucent canvas layers,
 * and attaches the Haze blur source for all layered glass cards and buttons.
 */
@Composable
fun GlassBackgroundCanvas(
    modifier: Modifier = Modifier,
    hazeState: HazeState = remember { HazeState() },
    content: @Composable BoxScope.() -> Unit
) {
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AutoBackground)
                .drawBehind {
                    // Top-Right Glowing Amber Light Orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AutoAmberPrimary.copy(alpha = 0.22f),
                                AutoAmberContainer.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.12f),
                            radius = size.width * 0.75f
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.12f),
                        radius = size.width * 0.75f
                    )

                    // Bottom-Left Subdued Eco Green Accent Orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AutoStatusCompleted.copy(alpha = 0.12f),
                                AutoAmberPrimary.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.10f, size.height * 0.85f),
                            radius = size.width * 0.70f
                        ),
                        center = Offset(size.width * 0.10f, size.height * 0.85f),
                        radius = size.width * 0.70f
                    )

                    // Central subtle highlight glow for deep glass contrast
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x1A2A2A2A),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.4f),
                            radius = size.width * 0.85f
                        )
                    )
                }
                .haze(hazeState)
        ) {
            content()
        }
    }
}

/**
 * GlassSurface is a base translucent container with frosted glass gradients,
 * 8.dp elevation shadow, Haze blur, 10-15% tint surface, and 1.dp vertical gradient border.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.12f),
    borderBrush: Brush = GlassBorderGradient,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState = LocalHazeState.current
    var boxModifier = modifier
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)

    if (hazeState != null) {
        boxModifier = boxModifier.hazeEffect(
            state = hazeState,
            style = HazeDefaults.style(
                backgroundColor = backgroundColor,
                tint = HazeTint(backgroundColor),
                blurRadius = 20.dp
            )
        )
    } else {
        boxModifier = boxModifier.background(backgroundColor)
    }

    boxModifier = boxModifier.border(width = borderWidth, brush = borderBrush, shape = shape)

    if (onClick != null) {
        boxModifier = boxModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = AutoAmberPrimary),
            onClick = onClick
        )
    }

    Box(
        modifier = boxModifier,
        content = content
    )
}

/**
 * Base screen composable utilizing AutoAlert glassmorphism style.
 * Includes translucent top/bottom bars, ambient glowing light canvas,
 * blurred card containers, soft borders, and adaptable scrollable content layout.
 */
@Composable
fun GlassBaseScreen(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    onNotificationClick: (() -> Unit)? = null,
    hasUnread: Boolean = false,
    selectedTab: NavTab? = null,
    onTabSelected: ((NavTab) -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = if (title != null) {
        {
            GlassTopBar(
                title = title,
                subtitle = subtitle,
                onNotificationClick = onNotificationClick,
                hasUnread = hasUnread
            )
        }
    } else null,
    bottomBar: (@Composable () -> Unit)? = if (selectedTab != null && onTabSelected != null) {
        {
            GlassBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected
            )
        }
    } else null,
    scrollable: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Scaffold(
        topBar = { topBar?.invoke() },
        bottomBar = { bottomBar?.invoke() },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { innerPadding ->
        GlassBackgroundCanvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = if (scrollable) {
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding)
                } else {
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                },
                content = content
            )
        }
    }
}

/**
 * GlassCard conforms to the exact Glassmorphism specification:
 * - 8.dp elevation shadow with matched corner radius
 * - Haze blur background layer for true blur on Android 12+ with graceful fallback
 * - Translucent surface with 10-15% opacity white/color tint
 * - 1.dp border using vertical gradient from white (40% opacity) to white (5% opacity)
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    borderBrush: Brush = GlassBorderGradient,
    borderColor: Color? = null,
    backgroundColor: Color = Color.White.copy(alpha = 0.12f),
    elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState = LocalHazeState.current
    val shape = RoundedCornerShape(cornerRadius)
    val finalBorderBrush = if (borderColor != null) Brush.verticalGradient(listOf(borderColor, borderColor.copy(alpha = 0.3f))) else borderBrush

    var boxModifier = modifier
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)

    if (hazeState != null) {
        boxModifier = boxModifier.hazeEffect(
            state = hazeState,
            style = HazeDefaults.style(
                backgroundColor = backgroundColor,
                tint = HazeTint(backgroundColor),
                blurRadius = 20.dp
            )
        )
    } else {
        boxModifier = boxModifier.background(backgroundColor)
    }

    boxModifier = boxModifier.border(width = 1.dp, brush = finalBorderBrush, shape = shape)

    if (onClick != null) {
        boxModifier = boxModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = AutoAmberPrimary),
            onClick = onClick
        )
    }

    Box(
        modifier = boxModifier.padding(16.dp),
        content = content
    )
}

/**
 * GlassButton conforms to the exact Glassmorphism specification:
 * - 8.dp elevation shadow with rounded corner shape
 * - Haze blur background layer
 * - Translucent surface with 10-15% opacity tint
 * - 1.dp vertical gradient border (white 40% -> 5%)
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50.dp),
    tintColor: Color = if (enabled) AutoAmberPrimary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
    textColor: Color = if (enabled) AutoAmberPrimary else AutoOnSurfaceVariant,
    borderBrush: Brush = GlassBorderGradient,
    elevation: Dp = 8.dp,
    testTagStr: String = "glass_button"
) {
    val hazeState = LocalHazeState.current
    var boxModifier = modifier
        .testTag(testTagStr)
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)

    if (hazeState != null) {
        boxModifier = boxModifier.hazeEffect(
            state = hazeState,
            style = HazeDefaults.style(
                backgroundColor = tintColor,
                tint = HazeTint(tintColor),
                blurRadius = 20.dp
            )
        )
    } else {
        boxModifier = boxModifier.background(tintColor)
    }

    boxModifier = boxModifier
        .border(width = 1.dp, brush = borderBrush, shape = shape)
        .clickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = AutoAmberPrimary),
            onClick = onClick
        )
        .padding(horizontal = 24.dp, vertical = 14.dp)

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            if (icon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * GlassReplyButton provides consistent glassmorphic action buttons for Coming / Busy replies:
 * - 8.dp elevation shadow
 * - 10-15% color tint (Emerald for Coming, Red/Coral for Busy)
 * - Haze blur background layer
 * - 1.dp vertical gradient border
 */
@Composable
fun GlassReplyButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    testTagStr: String = "reply_button"
) {
    val hazeState = LocalHazeState.current
    val shape = RoundedCornerShape(10.dp)
    val tintColor = color.copy(alpha = 0.15f)

    var boxModifier = modifier
        .testTag(testTagStr)
        .shadow(elevation = 8.dp, shape = shape, clip = false)
        .clip(shape)

    if (hazeState != null) {
        boxModifier = boxModifier.hazeEffect(
            state = hazeState,
            style = HazeDefaults.style(
                backgroundColor = tintColor,
                tint = HazeTint(tintColor),
                blurRadius = 16.dp
            )
        )
    } else {
        boxModifier = boxModifier.background(tintColor)
    }

    boxModifier = boxModifier
        .border(width = 1.dp, brush = GlassBorderGradient, shape = shape)
        .clickable(
            enabled = enabled && !isLoading,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = color),
            onClick = onClick
        )
        .padding(vertical = 10.dp)

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = color,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (badgeColor, textColor, text) = when (status.lowercase()) {
        "pending" -> Triple(AutoStatusPending.copy(alpha = 0.15f), AutoStatusPending, "Pending")
        "completed" -> Triple(AutoStatusCompleted.copy(alpha = 0.15f), AutoStatusCompleted, "Completed")
        "cancelled" -> Triple(AutoStatusCancelled.copy(alpha = 0.15f), AutoStatusCancelled, "Cancelled")
        "low battery", "lowbattery" -> Triple(AutoStatusLowBattery.copy(alpha = 0.2f), AutoStatusLowBattery, "Low Battery")
        else -> Triple(AutoAmberContainer.copy(alpha = 0.15f), AutoAmberPrimary, status)
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(badgeColor)
            .border(1.dp, textColor.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    errorMessage: String? = null,
    testTagStr: String = "input_field"
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder, color = AutoOnSurfaceVariant.copy(alpha = 0.5f)) },
            leadingIcon = if (leadingIcon != null) {
                { Icon(leadingIcon, contentDescription = null, tint = AutoAmberPrimary) }
            } else null,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            isError = isError,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTagStr),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AutoSurfaceHigh.copy(alpha = 0.6f),
                unfocusedContainerColor = AutoSurfaceHigh.copy(alpha = 0.4f),
                focusedBorderColor = AutoAmberContainer,
                unfocusedBorderColor = AutoGlassBorder,
                focusedLabelColor = AutoAmberPrimary,
                unfocusedLabelColor = AutoOnSurfaceVariant,
                focusedTextColor = AutoOnSurface,
                unfocusedTextColor = AutoOnSurface
            )
        )
        if (isError && !errorMessage.isNull_orEmpty()) {
            Text(
                text = errorMessage ?: "",
                color = AutoStatusCancelled,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun GlassTopBar(
    title: String,
    subtitle: String? = null,
    onNotificationClick: (() -> Unit)? = null,
    hasUnread: Boolean = false
) {
    val hazeState = LocalHazeState.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeDefaults.style(
                            backgroundColor = Color.Black.copy(alpha = 0.55f),
                            blurRadius = 24.dp
                        )
                    )
                } else {
                    Modifier.background(Color(0xCC131313))
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RectangleShape
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AutoOnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNull_orEmpty()) {
                    Text(
                        text = subtitle ?: "",
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            if (onNotificationClick != null) {
                Box {
                    IconButton(
                        onClick = onNotificationClick,
                        modifier = Modifier
                            .size(48.dp)
                            .glassmorphic(
                                shape = CircleShape,
                                tintColor = Color.White.copy(alpha = 0.12f),
                                elevation = 6.dp
                            )
                            .testTag("top_bar_notifications")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (hasUnread) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp, end = 4.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(AutoAmberContainer)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }
        }
    }
}

enum class NavTab {
    HOME, ORIENTATION_3D, HISTORY, INSIGHTS, SETTINGS, STATUS
}

@Composable
fun GlassBottomNavigation(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit
) {
    val strings = LocalStrings.current
    val hazeState = LocalHazeState.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeDefaults.style(
                            backgroundColor = Color.Black.copy(alpha = 0.65f),
                            blurRadius = 24.dp
                        )
                    )
                } else {
                    Modifier.background(Color(0xEE131313))
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RectangleShape
            )
            .navigationBarsPadding()
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                tab = NavTab.HOME,
                selected = selectedTab == NavTab.HOME,
                icon = Icons.Default.Home,
                label = strings.navHome,
                onClick = { onTabSelected(NavTab.HOME) },
                modifier = Modifier.weight(1f)
            )
            NavItem(
                tab = NavTab.ORIENTATION_3D,
                selected = selectedTab == NavTab.ORIENTATION_3D,
                icon = Icons.Default.Sensors,
                label = strings.navOrientation3D,
                onClick = { onTabSelected(NavTab.ORIENTATION_3D) },
                modifier = Modifier.weight(1f)
            )
            NavItem(
                tab = NavTab.HISTORY,
                selected = selectedTab == NavTab.HISTORY,
                icon = Icons.Default.History,
                label = strings.navHistory,
                onClick = { onTabSelected(NavTab.HISTORY) },
                modifier = Modifier.weight(1f)
            )
            NavItem(
                tab = NavTab.INSIGHTS,
                selected = selectedTab == NavTab.INSIGHTS,
                icon = Icons.Default.Insights,
                label = strings.navInsights,
                onClick = { onTabSelected(NavTab.INSIGHTS) },
                modifier = Modifier.weight(1f)
            )
            NavItem(
                tab = NavTab.SETTINGS,
                selected = selectedTab == NavTab.SETTINGS,
                icon = Icons.Default.Settings,
                label = strings.navSettings,
                onClick = { onTabSelected(NavTab.SETTINGS) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavItem(
    tab: NavTab,
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = tween(200),
        label = "tab_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = AutoAmberPrimary),
                onClick = onClick
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("nav_tab_${label.lowercase()}")
    ) {
        Box(
            modifier = Modifier.size(width = 44.dp, height = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) AutoAmberPrimary else AutoOnSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(24.dp)
                    .scale(scale)
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(AutoAmberContainer)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (selected) AutoAmberPrimary else AutoOnSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * CircularBatteryGauge displays a circular progress ring representing battery percentage:
 * - Fills proportionally to the battery percentage.
 * - Transitions color from AutoAmberPrimary / AutoStatusCompleted to AutoStatusCancelled (Red) below 20%.
 * - Shows the percentage centered in bold typography.
 */
@Composable
fun CircularBatteryGauge(
    batteryPercent: Int,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strokeWidth: Dp = 5.dp,
    showLabel: Boolean = true
) {
    val clampedPercent = batteryPercent.coerceIn(0, 100)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedPercent / 100f,
        animationSpec = tween(durationMillis = 500),
        label = "battery_progress"
    )
    val ringColor = if (clampedPercent < 20) {
        AutoStatusCancelled
    } else if (clampedPercent > 60) {
        AutoStatusCompleted
    } else {
        AutoAmberPrimary
    }
    val trackColor = AutoSurfaceHigh.copy(alpha = 0.5f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val arcSize = this.size.minDimension - strokePx
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            // Draw full background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                ),
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize)
            )

            // Draw progress arc
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                ),
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize)
            )
        }

        if (showLabel) {
            Text(
                text = "$clampedPercent%",
                color = AutoOnSurface,
                fontSize = if (size >= 64.dp) 15.sp else if (size >= 48.dp) 12.sp else 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * SimulationBadge displays a compact amber pill badge in the top corner indicating simulation mode.
 */
@Composable
fun SimulationBadge(
    modifier: Modifier = Modifier,
    text: String = "SIMULATION"
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(AutoAmberContainer.copy(alpha = 0.22f))
            .border(
                width = 1.dp,
                color = AutoAmberPrimary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(50.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AutoAmberPrimary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = AutoAmberPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }
    }
}

private fun String?.isNull_orEmpty(): Boolean = this == null || this.trim().isEmpty()

