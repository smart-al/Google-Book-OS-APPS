package com.droid.liquidglass3d

import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val settings: GlassSettings = GlassSettings(),
    val scene: SceneBitmaps? = null,
    val sceneVersion: Int = 0,
    val busyStage: String? = null,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState(settings = SceneStore.loadSettings(app)))
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val estimator = DepthEstimator(app)

    init {
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { SceneStore.loadScene(app) }
            if (s != null) _state.update { it.copy(scene = s, sceneVersion = it.sceneVersion + 1) }
        }
    }

    fun updateSettings(transform: (GlassSettings) -> GlassSettings) {
        val next = transform(_state.value.settings)
        _state.update { it.copy(settings = next) }
        SceneStore.saveSettings(getApplication(), next) // wallpaper engine picks this up live
    }

    fun selectMaskMode(mode: MaskMode) {
        updateSettings { it.copy(maskMode = mode) }
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { SceneStore.activateMaskMode(getApplication(), mode) }
            if (s != null) _state.update { it.copy(scene = s, sceneVersion = it.sceneVersion + 1) }
        }
    }

    fun loadDemo() {
        if (_state.value.busyStage != null) return
        _state.update { it.copy(busyStage = "Loading Sample 3D Scene…", error = null) }
        viewModelScope.launch {
            val scene = withContext(Dispatchers.IO) { SceneStore.loadDemoScene(getApplication()) }
            _state.update { it.copy(scene = scene, sceneVersion = it.sceneVersion + 1, busyStage = null) }
        }
    }

    fun onImagePicked(uri: Uri) {
        if (_state.value.busyStage != null) return
        _state.update { it.copy(busyStage = "Starting…", error = null) }
        viewModelScope.launch {
            try {
                val scene = estimator.process(uri) { stage -> _state.update { it.copy(busyStage = stage) } }
                _state.update { it.copy(scene = scene, sceneVersion = it.sceneVersion + 1, busyStage = null) }
            } catch (e: Exception) {
                _state.update { it.copy(busyStage = null, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LiquidGlassTheme { LiquidGlassApp() } }
    }
}

@Composable
private fun LiquidGlassTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = runCatching { dynamicDarkColorScheme(ctx) }.getOrElse { darkColorScheme() }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiquidGlassApp(vm: MainViewModel = viewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(vm::onImagePicked) }

    val pick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    val setWallpaper = {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, LiquidGlassWallpaperService::class.java),
        )
        context.startActivity(intent)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            val wide = maxWidth >= 840.dp // Google Book OS desktop & convertible landscape
            val preview = @Composable { mod: Modifier ->
                PreviewPane(ui, vm, mod, wide = wide, onPick = pick)
            }
            val controls = @Composable { mod: Modifier ->
                ControlsPane(ui, vm, mod, onPick = pick, onSetWallpaper = setWallpaper)
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1.5f).fillMaxHeight())
                    controls(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1.1f).fillMaxWidth())
                    controls(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Live AGSL preview rendered at 16:10 aspect ratio (Google Book OS standard).
 * Supports direct mouse dragging of the clock widget and 3D parallax on hover/touch.
 */
@Composable
private fun PreviewPane(
    ui: UiState,
    vm: MainViewModel,
    modifier: Modifier,
    wide: Boolean,
    onPick: () -> Unit,
) {
    val context = LocalContext.current
    val renderer = remember { LiquidGlassRenderer(context) }
    val parallax = remember { ParallaxController(context) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(0L) }
    var timeSec by remember { mutableStateOf(0f) }
    var isDraggingClock by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        parallax.start()
        onDispose { parallax.stop() }
    }

    // Re-prepare surface-sized textures when the pane resizes or a new image arrives.
    LaunchedEffect(size, ui.sceneVersion) {
        val scene = ui.scene
        if (scene == null || size.width <= 0 || size.height <= 0) { renderer.clear(); return@LaunchedEffect }
        val prepared = withContext(Dispatchers.Default) { renderer.prepare(scene, size.width, size.height) }
        renderer.apply(prepared)
    }

    // Per-frame loop
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = now
                timeSec += dt
                parallax.step(dt)
                frame = now
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Enforce 16:10 standard aspect ratio for Google Book laptop screens
        Box(
            Modifier
                .fillMaxSize()
                .aspectRatio(16f / 10f, matchHeightConstraintsFirst = wide)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .onSizeChanged { size = it }
                .pointerInput(ui.sceneVersion) {
                    awaitPointerEventScope {
                        var grabOffsetX = 0f
                        var grabOffsetY = 0f
                        while (true) {
                            val e = awaitPointerEvent()
                            val p = e.changes.firstOrNull() ?: continue
                            val px = p.position.x
                            val py = p.position.y
                            val panelRect = renderer.getPanelRect()
                            val hitTest = RectF(
                                panelRect.left - 24f,
                                panelRect.top - 24f,
                                panelRect.right + 24f,
                                panelRect.bottom + 24f
                            )

                            when (e.type) {
                                PointerEventType.Press -> {
                                    if (hitTest.contains(px, py)) {
                                        isDraggingClock = true
                                        grabOffsetX = px - panelRect.left
                                        grabOffsetY = py - panelRect.top
                                    } else {
                                        isDraggingClock = false
                                        parallax.onPointer(px, py, size.width.toFloat(), size.height.toFloat())
                                    }
                                }
                                PointerEventType.Move -> {
                                    if (isDraggingClock && size.width > 0 && size.height > 0) {
                                        val availW = (size.width - panelRect.width()).coerceAtLeast(1f)
                                        val availH = (size.height - panelRect.height()).coerceAtLeast(1f)
                                        val targetLeft = px - grabOffsetX
                                        val targetTop = py - grabOffsetY
                                        val newNormX = (targetLeft / availW).coerceIn(0f, 1f)
                                        val newNormY = (targetTop / availH).coerceIn(0f, 1f)
                                        vm.updateSettings { it.copy(clockPosX = newNormX, clockPosY = newNormY) }
                                    } else {
                                        parallax.onPointer(px, py, size.width.toFloat(), size.height.toFloat())
                                    }
                                }
                                PointerEventType.Release, PointerEventType.Exit -> {
                                    isDraggingClock = false
                                    parallax.onPointerExit()
                                }
                                PointerEventType.Enter -> {
                                    parallax.onPointer(px, py, size.width.toFloat(), size.height.toFloat())
                                }
                            }
                        }
                    }
                }
                .drawBehind {
                    frame // Invalidate every frame
                    drawIntoCanvas { c ->
                        renderer.draw(c.nativeCanvas, parallax.x, parallax.y, ui.settings, timeSec)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (ui.scene == null && ui.busyStage == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Pick any photo to generate 3D Liquid Glass Wallpaper",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = onPick) {
                            Icon(Icons.Filled.Image, null)
                            Spacer(Modifier.padding(4.dp))
                            Text("Choose image")
                        }
                        Button(onClick = { vm.loadDemo() }) {
                            Icon(Icons.Filled.CenterFocusStrong, null)
                            Spacer(Modifier.padding(4.dp))
                            Text("Try Sample Scene")
                        }
                    }
                }
            }
            if (ui.busyStage != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(ui.busyStage, color = Color.White)
                }
            }

            // Top Status & Drag Hint Badge
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isDraggingClock) Icons.Filled.OpenWith else Icons.Filled.CenterFocusStrong,
                    contentDescription = null,
                    tint = if (isDraggingClock) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (isDraggingClock) "Moving Clock with Mouse…" else "Google Book 16:10 · Drag clock to move",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.90f),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsPane(
    ui: UiState,
    vm: MainViewModel,
    modifier: Modifier,
    onPick: () -> Unit,
    onSetWallpaper: () -> Unit,
) {
    val s = ui.settings
    Card(modifier) {
        Column(
            Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("LiquidGlass3D", style = MaterialTheme.typography.headlineMedium)

            // AI Mask Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val detected = ui.scene?.detectedMode?.label
                val subtitle = if (s.maskMode == MaskMode.AUTO && detected != null) {
                    "AI Subject Mask · Detected: $detected"
                } else {
                    "AI Subject Mask"
                }
                Text(subtitle, style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MaskMode.entries.forEach { mode ->
                        FilterChip(
                            selected = s.maskMode == mode,
                            onClick = { vm.selectMaskMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }

            // 3D Parallax & Refraction Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("3D Spatial Mode", style = MaterialTheme.typography.titleMedium)
                    Text("Parallax from tilt, pointer & desktop offset", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = s.spatialEnabled, onCheckedChange = { v -> vm.updateSettings { it.copy(spatialEnabled = v) } })
            }

            LabeledSlider("Depth Intensity", s.depthIntensity, 0f..1.5f, enabled = s.spatialEnabled) { v ->
                vm.updateSettings { it.copy(depthIntensity = v) }
            }
            LabeledSlider("Liquid Glass Refraction", s.refraction, 0f..1.5f) { v ->
                vm.updateSettings { it.copy(refraction = v) }
            }
            LabeledSlider("Glass Image Blending", s.glassBlend, 0f..1f) { v ->
                vm.updateSettings { it.copy(glassBlend = v) }
            }

            HorizontalDivider()

            // Clock & Date Customization
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Clock & Date", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("Show Date", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Switch(checked = s.showDate, onCheckedChange = { v -> vm.updateSettings { it.copy(showDate = v) } })
            }

            // Depth Layer Order (Behind vs In Front of Subject)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Clock Depth Layer", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    text = if (s.clockInFront) "Clock floats on top of the image" else "Clock is depth-occluded behind the foreground subject",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !s.clockInFront,
                        onClick = { vm.updateSettings { it.copy(clockInFront = false) } },
                        label = { Text("Behind Subject") },
                    )
                    FilterChip(
                        selected = s.clockInFront,
                        onClick = { vm.updateSettings { it.copy(clockInFront = true) } },
                        label = { Text("In Front of Subject") },
                    )
                }
            }

            // Clock Size Slider
            LabeledSlider("Clock Size", s.clockScale, 0.65f..1.45f) { v ->
                vm.updateSettings { it.copy(clockScale = v) }
            }

            // Movable Clock Position Controls
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Clock Position", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = { vm.updateSettings { it.copy(clockPosX = 0.5f, clockPosY = 0.16f) } },
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("Center", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text("Drag directly with mouse in the 16:10 preview, or adjust:", style = MaterialTheme.typography.bodySmall)
                LabeledSlider("Horizontal (X)", s.clockPosX, 0f..1f) { v ->
                    vm.updateSettings { it.copy(clockPosX = v) }
                }
                LabeledSlider("Vertical (Y)", s.clockPosY, 0f..1f) { v ->
                    vm.updateSettings { it.copy(clockPosY = v) }
                }
            }

            // Clock Font Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Clock Font", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ClockFont.entries.forEach { font ->
                        FilterChip(
                            selected = s.clockFont == font,
                            onClick = { vm.updateSettings { it.copy(clockFont = font) } },
                            label = { Text(font.label) },
                        )
                    }
                }
            }

            // Colors & Mixed Color Combinations
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Clock Colors & Mixed Gradients", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(s.clockColorPreset.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }

                Text("Solid Glass Colors (8)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ClockColorPreset.entries.filter { !it.isGradient }.forEach { preset ->
                        ColorSwatch(preset = preset, selected = s.clockColorPreset == preset) {
                            vm.updateSettings { it.copy(clockColorPreset = preset) }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Liquid Gradients & Mixed Tones (28)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ClockColorPreset.entries.filter { it.isGradient }.forEach { preset ->
                        ColorSwatch(preset = preset, selected = s.clockColorPreset == preset) {
                            vm.updateSettings { it.copy(clockColorPreset = preset) }
                        }
                    }
                }
            }

            ui.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onPick, enabled = ui.busyStage == null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Image, null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Choose image")
                }
                OutlinedButton(onClick = { vm.loadDemo() }, enabled = ui.busyStage == null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CenterFocusStrong, null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Sample Scene")
                }
            }
            Button(onClick = onSetWallpaper, enabled = ui.scene != null && ui.busyStage == null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Wallpaper, null)
                Spacer(Modifier.padding(4.dp))
                Text("Set Wallpaper")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ColorSwatch(preset: ClockColorPreset, selected: Boolean, onClick: () -> Unit) {
    val brush = if (preset.isGradient) {
        Brush.linearGradient(preset.colors.map { Color(it) })
    } else {
        Brush.linearGradient(listOf(Color(preset.colors[0]), Color(preset.colors[0])))
    }

    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(brush)
            .border(
                width = if (selected) 3.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.40f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text("%.2f".format(value), style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, enabled = enabled)
    }
}
