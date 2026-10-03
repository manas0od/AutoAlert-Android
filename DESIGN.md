---
name: AutoAlert Design System
version: 1.0.0
description: Design system for AutoAlert — a driver utility app blending glassmorphism with Kerala auto-rickshaw identity.
colors:
  primary:
    amber_primary: "#FFC688"
    amber_container: "#FF9F0A"
    amber_dim: "#FFFFB868"
    on_primary: "#673D00"
  surface:
    background: "#131313"
    surface: "#1F1F1F"
    surface_high: "#2A2A2A"
    surface_highest: "#353535"
    on_surface: "#E2E2E2"
    on_surface_variant: "#DAC3AD"
  status:
    pending: "#FF9F0A"
    completed_coming: "#81C784"
    cancelled_busy: "#E57373"
    low_battery: "#FF7043"
  glass:
    tint_default: "rgba(255, 255, 255, 0.12)"
    tint_active_amber: "rgba(255, 159, 10, 0.15)"
    tint_coming_emerald: "rgba(129, 199, 132, 0.15)"
    tint_busy_coral: "rgba(229, 115, 115, 0.15)"
    border_gradient_start: "rgba(255, 255, 255, 0.40)"
    border_gradient_end: "rgba(255, 255, 255, 0.05)"
typography:
  font_family: "System Default (Roboto / Sans-Serif)"
  scale:
    headline_large: { size: "32.sp", weight: "Bold", line_height: "40.sp" }
    headline_medium: { size: "24.sp", weight: "Bold", line_height: "30.sp" }
    title_large: { size: "20.sp", weight: "SemiBold", line_height: "26.sp" }
    title_medium: { size: "16.sp", weight: "Medium", line_height: "22.sp" }
    body_large: { size: "16.sp", weight: "Normal", line_height: "24.sp" }
    body_medium: { size: "14.sp", weight: "Normal", line_height: "20.sp" }
    label_large: { size: "14.sp", weight: "Bold", line_height: "18.sp" }
    label_medium: { size: "12.sp", weight: "Medium", line_height: "16.sp" }
    label_small: { size: "11.sp", weight: "Bold", line_height: "14.sp" }
border_radius:
  pill: "50.dp"
  surface_large: "20.dp"
  card: "18.dp"
  card_compact: "12.dp"
  action_small: "10.dp"
  circle: "CircleShape"
elevation_and_shadow:
  glass_elevation: "8.dp"
  top_bar_elevation: "6.dp"
spacing:
  xs: "4.dp"
  sm: "8.dp"
  md: "12.dp"
  lg: "16.dp"
  xl: "20.dp"
  xxl: "24.dp"
  xxxl: "32.dp"
---

# AutoAlert Design System

## 1. Brand Philosophy & Identity
**AutoAlert** is a driver utility app built for auto-rickshaw drivers across Kerala. It unites modern **frosted glassmorphism** with the iconic **Kerala auto-rickshaw identity** (deep matte black bodies, warm chrome amber lights, and grounded utilitarian textures).

The aesthetic is designed to feel:
- **Warm & Grounded**: Inspired by Kerala streets, auto meters, and stand culture — never corporate, clinical, or cold.
- **High Legibility in Sunlight & Night**: High-contrast typography (`#E2E2E2`) atop deep night-mode surfaces (`#131313`) prevents glare during daytime driving and eye fatigue at night.
- **Tactile & Responsive**: High-touch targets (minimum 48dp), clear 8dp elevation shadows, and instant visual ripple feedback.

---

## 2. Color Application Rules

### Amber (`#FFC688`, `#FF9F0A`) — Primary & High-Priority Actions
- **Usage**: Reserve amber exclusively for primary interactive calls-to-action (e.g., *Get Started*, *Connect Device*, active tab highlights, active call badges, unread indicators).
- **Rule**: Never use solid amber as large background canvases. Amber is an energetic signal light, not a wall paint.

### Dark Surfaces (`#131313`, `#1F1F1F`, `#2A2A2A`) — Scaffolding & Canvas
- **Background (`#131313`)**: Used for root application window backdrops, embedded with warm amber and emerald radial ambient light orbs via `GlassBackgroundCanvas`.
- **Surface High (`#2A2A2A`) / Surface Highest (`#353535`)**: Used for background layering beneath glass containers.

### Functional Status Accents
- **Coming / Active / Success**: Emerald Green (`#81C784`) — used for driver "Coming" action replies, device online status, and success toasts.
- **Busy / Cancelled / Error**: Coral Red (`#E57373`) — used for driver "Busy" action replies, offline warnings, and disconnect alerts.
- **Low Battery / Warning**: Deep Coral (`#FF7043`) — used for device hardware warnings.

---

## 3. Glassmorphism Specification (Haze Engine)

> **Mandatory Rule**: All cards, buttons, dialogs, sheets, top bars, and navigation menus MUST strictly adhere to the glassmorphic spec. **Never mix flat, solid, or opaque material cards with frosted glass.**

Every glass component must follow this 4-step execution:

```
┌────────────────────────────────────────────────────────┐
│ 1. Elevation Shadow: 8.dp shadow with matched corner   │
│ 2. Blur: Haze blur effect behind content (20.dp blur)  │
│ 3. Translucent Tint: 10-15% opacity white or accent    │
│ 4. Border: 1.dp vertical gradient (white 40% -> 5%)    │
└────────────────────────────────────────────────────────┘
```

### Exact Parameter Values:
1. **Translucent Surface**:
   - Standard Cards / Sheets: `Color.White.copy(alpha = 0.12f)`
   - Primary Buttons: `AutoAmberPrimary.copy(alpha = 0.15f)`
   - "Coming" Reply Action: `AutoStatusCompleted.copy(alpha = 0.15f)`
   - "Busy" Reply Action: `AutoStatusCancelled.copy(alpha = 0.15f)`
2. **Haze Blur Effect**:
   - Ambient Source: Root screens wrap their canvas with `GlassBackgroundCanvas` containing `Modifier.haze(hazeState)`.
   - Child Modifier: Components apply `Modifier.hazeEffect(state = hazeState, style = HazeDefaults.style(backgroundColor = tintColor, blurRadius = 20.dp))` or use `Modifier.glassmorphic(...)`.
   - Fallback: Gracefully falls back to translucent tint on pre-Android 12 devices without manual checks.
3. **Curved Light Border**:
   - `1.dp` border with `Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.05f)))`.
4. **Elevation & Shape**:
   - `Modifier.shadow(elevation = 8.dp, shape = shape, clip = false)` ensuring ambient depth against the dark backdrop.

---

## 4. Component Inventory & Standards

### Glass Buttons (`GlassButton`, `GlassReplyButton`)
- Corner radius: `50.dp` (pill) for primary CTA buttons; `10.dp` / `12.dp` for contextual reply buttons.
- Height / Padding: Minimum 48dp touch target with `horizontal = 24.dp, vertical = 14.dp`.

### Glass Cards (`GlassCard`, `GlassSurface`)
- Corner radius: `18.dp` or `20.dp`.
- Padding: `16.dp` to `20.dp` interior spacing.
- Usage: `Device Status Card`, `Today's Summary Card`, `Call Notification Card`, `Settings Section Containers`.

### Navigation & Top Bars (`GlassTopBar`, `GlassBottomNavigation`)
- Translucent dark haze tint (`Color.Black.copy(alpha = 0.65f)`) with 1.dp vertical gradient light rim.
- Edge-to-edge system insets handling with `statusBarsPadding()` and `navigationBarsPadding()`.

---

## 5. Implementation Guidance for New Features
When building any new screen or component:
1. Wrap the screen in `GlassBackgroundCanvas`.
2. Reuse `GlassCard`, `GlassButton`, `GlassReplyButton`, `GlassTextField`, or the `Modifier.glassmorphic(...)` extension from `GlassComponents.kt`.
3. Never use opaque solid backgrounds (`Color.White`, `Color.Black`, `Color.Gray`) for cards or dialogs.
4. Keep typography paired to high-contrast `#E2E2E2` and secondary `#DAC3AD`.
