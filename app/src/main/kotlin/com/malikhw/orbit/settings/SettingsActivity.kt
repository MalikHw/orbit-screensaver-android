package com.malikhw.orbit.settings

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.InputDevice
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.scrollBy // fuck
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.malikhw.orbit.BuildConfig
import com.malikhw.orbit.dream.OrbitRenderer
import com.malikhw.orbit.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ENABLE_UPDATER: Boolean get() = BuildConfig.ENABLE_UPDATER

class SettingsActivity : ComponentActivity() {

    private var pendingImageCallback: ((Uri?) -> Unit)? = null

    // ACTION_OPEN_DOCUMENT gives a persistable URI that survives reboots
    // when combined with takePersistableUriPermission()
    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            uri?.let {
                try {
                    contentResolver.takePersistableUriPermission(
                        it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
            }
            pendingImageCallback?.invoke(uri)
            pendingImageCallback = null
        }

    fun launchImagePicker(onResult: (Uri?) -> Unit) {
        pendingImageCallback = onResult
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            // Request persistable permission upfront
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        imagePickerLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContent {
            OrbitTheme {
                val scrollState = rememberScrollState()
                VirtualCursorWrapper(activity = this, scrollState = scrollState) {
                    SettingsScreen(activity = this, scrollState = scrollState)
                }
            }
        }
    }
}


@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary      = Color(0xFF64B5F6),
            secondary    = Color(0xFF81C784),
            background   = Color(0xFF121212),
            surface      = Color(0xFF1E1E1E),
            onPrimary    = Color.Black,
            onBackground = Color.White,
            onSurface    = Color.White,
        ),
        content = content
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(activity: SettingsActivity, scrollState: ScrollState = rememberScrollState()) {
    val context = LocalContext.current
    val prefs   = remember { OrbitPrefs(context) }

    val isChromebook = remember { context.packageManager.hasSystemFeature("org.chromium.arc") }
    val isTv         = remember {
        context.packageManager.hasSystemFeature("android.software.leanback") ||
        context.packageManager.hasSystemFeature("android.hardware.type.television")
    }

    var speed      by remember { mutableIntStateOf(prefs.speed) }
    var fps        by remember { mutableIntStateOf(prefs.fps) }
    var bgMode     by remember { mutableIntStateOf(prefs.bgMode) }
    var bgColor    by remember { mutableStateOf(Color(prefs.bgColorR, prefs.bgColorG, prefs.bgColorB)) }
    var noGround   by remember { mutableStateOf(prefs.noGround) }
    var orbScale   by remember { mutableFloatStateOf(prefs.orbScale) }
    var orbCount   by remember { mutableIntStateOf(prefs.orbCount) }
    var cubeChance by remember { mutableIntStateOf(prefs.cubeChance) }
    var bgImageUri by remember { mutableStateOf(prefs.bgImageUri) }
    var cubeUri    by remember { mutableStateOf(prefs.cubeImageUri) }

    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var saveToast   by remember { mutableStateOf(false) }

    fun pickBgImage() {
        activity.launchImagePicker { uri ->
            uri?.let {
                bgImageUri = it.toString()
                prefs.bgImageUri = it.toString()
            }
        }
    }

    fun pickCubeImage() {
        activity.launchImagePicker { uri ->
            uri?.let {
                cubeUri = it.toString()
                prefs.cubeImageUri = it.toString()
            }
        }
    }

    fun save() {
        prefs.speed      = speed
        prefs.fps        = fps
        prefs.bgMode     = bgMode
        prefs.bgColorR   = bgColor.red
        prefs.bgColorG   = bgColor.green
        prefs.bgColorB   = bgColor.blue
        prefs.noGround   = noGround
        prefs.orbScale   = orbScale
        prefs.orbCount   = orbCount
        prefs.cubeChance = cubeChance
        saveToast = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Orbit Screensaver", fontWeight = FontWeight.Bold)
                        Text("by MalikHw47", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                },
                actions = {
                    IconButton(onClick = { save() }) {
                        Icon(Icons.Default.Save, "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
                .focusable(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // chromebook warning
            if (isChromebook) {
                var dismissed by remember { mutableStateOf(false) }
                if (!dismissed) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF7B2D00)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB74D))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Chromebook detected", fontWeight = FontWeight.Bold,
                                    color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                Text("This is not supported on Chrome OS. The screensaver won't activate. (not my fault forgive me 💀)",
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { dismissed = true }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
                            }
                        }
                    }
                }
            }

            // TV hint
            if (isTv) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF82B1FF))
                        Column {
                            Text("TV / D-pad mode", fontWeight = FontWeight.Bold,
                                color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text("Use D-pad or gamepad to navigate. Press Center/A to interact.",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            SectionCard("Physics") {
                LabeledSlider("Speed: $speed", speed.toFloat(), 1f, 20f) { speed = it.toInt() }
                Spacer(Modifier.height(8.dp))
                LabeledSlider("FPS: $fps", fps.toFloat(), 15f, 165f) { fps = it.toInt() }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("30" to 30, "60" to 60, "90" to 90, "120" to 120, "165" to 165).forEach { (label, v) ->
                        FilterChip(selected = fps == v, onClick = { fps = v }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Infinite fall", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = noGround, onCheckedChange = { noGround = it }, modifier = Modifier.tvFocusBorder(RoundedCornerShape(50)))
                }
            }


            SectionCard("Orbs") {
                LabeledSlider("Count: $orbCount", orbCount.toFloat(), 1f, 300f) { orbCount = it.toInt() }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Low" to 30, "Med" to 80, "High" to 120, "Giga" to 210).forEach { (label, v) ->
                        FilterChip(selected = orbCount == v, onClick = { orbCount = v }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                LabeledSlider("Size: ${"%.1f".format(orbScale)}×", orbScale, 0.3f, 3.0f) { orbScale = it }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("S" to 0.5f, "M" to 1.0f, "L" to 1.5f, "XL" to 2.0f).forEach { (label, v) ->
                        FilterChip(selected = orbScale == v, onClick = { orbScale = v }, label = { Text(label) })
                    }
                }
            }

            SectionCard("Cube") {
                LabeledSlider("Spawn chance: $cubeChance%", cubeChance.toFloat(), 0f, 100f) { cubeChance = it.toInt() }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("custom cube image", style = MaterialTheme.typography.bodyMedium)
                        if (cubeUri != null)
                            Text(uriFilename(context, cubeUri!!), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        else
                            Text("Using bundled default", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { pickCubeImage() }, modifier = Modifier.tvFocusBorder()) { Text("Browse") }
                        if (cubeUri != null) {
                            OutlinedButton(onClick = { cubeUri = null; prefs.cubeImageUri = null }, modifier = Modifier.tvFocusBorder()) { Text("Reset") }
                        }
                    }
                }
            }

            // background
            SectionCard("Background") {
                val bgOptions = listOf("Black" to OrbitPrefs.BG_BLACK, "Color" to OrbitPrefs.BG_COLOR, "Image" to OrbitPrefs.BG_IMAGE)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    bgOptions.forEach { (label, value) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { bgMode = value }.padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(selected = bgMode == value, onClick = { bgMode = value }, modifier = Modifier.tvFocusBorder(CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (bgMode == OrbitPrefs.BG_COLOR) {
                    Spacer(Modifier.height(12.dp))
                    ColorPickerRow(bgColor) { bgColor = it }
                }
                if (bgMode == OrbitPrefs.BG_IMAGE) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background image", style = MaterialTheme.typography.bodyMedium)
                            if (bgImageUri != null)
                                Text(uriFilename(context, bgImageUri!!), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            else
                                Text("None selected", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        OutlinedButton(onClick = { pickBgImage() }, modifier = Modifier.tvFocusBorder()) { Text("Browse") }
                    }
                }
            }

            // updates (hidden in Play Store variant)
            if (ENABLE_UPDATER) {
                SectionCard("Updates") {
                    val scope = rememberCoroutineScope()
                    when (val state = updateState) {
                        is UpdateState.Idle -> {
                            Button(
                                onClick = {
                                    updateState = UpdateState.Checking
                                    scope.launch {
                                        val info = UpdateChecker.fetchLatest()
                                        updateState = if (info == null) UpdateState.Error("Could not reach GitHub")
                                        else UpdateState.Result(info)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Check for updates") }
                        }
                        is UpdateState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Checking…", color = Color.Yellow)
                            }
                        }
                        is UpdateState.Error -> {
                            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { updateState = UpdateState.Idle }) { Text("Retry") }
                        }
                        is UpdateState.Result -> {
                            val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            if (state.info.tag == appVersion || state.info.tag == "v$appVersion") {
                                Text("✓ You're up to date! (${state.info.tag})", color = MaterialTheme.colorScheme.secondary)
                            } else {
                                Text("Update available: ${state.info.tag}", color = Color(0xFFFF9800))
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        updateState = UpdateState.Downloading(0)
                                        scope.launch {
                                            try {
                                                UpdateChecker.downloadAndInstall(context, state.info) { progress ->
                                                    updateState = UpdateState.Downloading(progress)
                                                }
                                                updateState = UpdateState.Idle
                                            } catch (e: Exception) {
                                                updateState = UpdateState.Error("Download failed: ${e.message}")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Download & Install") }
                            }
                        }
                        is UpdateState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("Downloading… ${state.progress}%", color = Color.Yellow)
                                }
                                LinearProgressIndicator(
                                    progress = { state.progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // links
            SectionCard("Author") {
                val links = listOf(
                    "Website" to "https://malikhw.github.io",
                    "YouTube" to "https://youtube.com/@MalikHw47",
                    "GitHub"  to "https://github.com/MalikHw",
                    "Twitch"  to "https://twitch.tv/MalikHw47",
                    "Discord" to "https://discord.gg/G9bZ92eg2n",
                    "Throne"  to "https://throne.com/MalikHw47",
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    links.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (label, url) ->
                                OutlinedButton(
                                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                                    modifier = Modifier.weight(1f).tvFocusBorder(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) { Text(label, fontSize = 12.sp) }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    DonateButton(activity = activity)
                }
            }

            // bottom save
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(50.dp).tvFocusBorder(RoundedCornerShape(50))) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Settings", fontSize = 16.sp)
            }

            var dreamSettingsError by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(Intent().apply {
                            setClassName("com.android.settings", "com.android.settings.Settings\$DreamSettingsActivity")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        dreamSettingsError = false
                    } catch (e: Exception) {
                        try {
                            context.startActivity(Intent().apply {
                                setClassName("com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            dreamSettingsError = false
                        } catch (e2: Exception) {
                            dreamSettingsError = true
                            android.widget.Toast.makeText(context, "Couldn't open screensaver settings...\nNot supported on this device :(", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).tvFocusBorder(RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Android Screensaver Settings", fontSize = 14.sp)
            }
            if (dreamSettingsError) {
                Text("Couldn't open screensaver settings on this device, Maybe your device manufacturer deleted the screensaver feature from your phone 🥀",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp))
            }

            // live prev
            SectionCard("Preview") {
                Text(
                    "Live screensaver preview — reacts to your settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    OrbitPreview(
                        speed = speed,
                        fps = fps,
                        bgMode = bgMode,
                        bgColorR = bgColor.red,
                        bgColorG = bgColor.green,
                        bgColorB = bgColor.blue,
                        noGround = noGround,
                        orbScale = orbScale,
                        orbCount = orbCount,
                        cubeChance = cubeChance,
                        bgImageUri = bgImageUri,
                        cubeUri = cubeUri,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    )
                    IconButton(
                        onClick = {
                            context.startActivity(
                                android.content.Intent(context, FullscreenPreviewActivity::class.java) // i love it
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(36.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Preview fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // dihclaimer
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Disclaimer: some assets are NOT by me, they're by RobtopGames from the game Geometry Dash.",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(24.dp))
        }

        if (saveToast) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1500)
                saveToast = false
            }
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary, tonalElevation = 8.dp) {
                    Text("Saved!", modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Reusable composables
@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, min: Float, max: Float, onChanged: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    val step = (max - min) / 20f  // 5% per D-pad press
    var isFocused by remember { mutableStateOf(false) }
    Slider(
        value        = value,
        onValueChange = onChanged,
        valueRange   = min..max,
        modifier     = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft  -> { onChanged((value - step).coerceIn(min, max)); true }
                        Key.DirectionRight -> { onChanged((value + step).coerceIn(min, max)); true }
                        else -> false
                    }
                } else false
            }
    )
}

@Composable
fun ColorPickerRow(color: Color, onColorChange: (Color) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(color)
            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            listOf("R" to color.red, "G" to color.green, "B" to color.blue).forEach { (ch, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ch, modifier = Modifier.width(16.dp), style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = v,
                        onValueChange = { nv ->
                            onColorChange(when (ch) { "R" -> color.copy(red = nv); "G" -> color.copy(green = nv); else -> color.copy(blue = nv) })
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// update state
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class Error(val message: String) : UpdateState()
    data class Result(val info: UpdateChecker.ReleaseInfo) : UpdateState()
}

// util
fun uriFilename(context: android.content.Context, uriString: String): String {
    return try {
        val uri = Uri.parse(uriString)
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else uriString
        } ?: uriString
    } catch (e: Exception) { uriString }
}
// TV / D-pad focus border, wrap any interactive element with this
@Composable
fun Modifier.tvFocusBorder(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)): Modifier {
    var focused by remember { mutableStateOf(false) }
    val borderColor = MaterialTheme.colorScheme.primary
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(
            width = if (focused) 3.dp else 0.dp,
            color = if (focused) borderColor else Color.Transparent,
            shape = shape
        )
        .focusable()
}

// Vcursor overlay (TV/keyboard(fuck android x86 users)/gamepad)
@Composable
fun VirtualCursorWrapper(activity: SettingsActivity, scrollState: ScrollState, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isTvOrNoTouch = remember {
        context.packageManager.hasSystemFeature("android.software.leanback") ||
        context.packageManager.hasSystemFeature("android.hardware.type.television") ||
        !context.packageManager.hasSystemFeature("android.hardware.touchscreen")
    }

    if (!isTvOrNoTouch) {
        // Phone/tablet: just render content normally
        content()
        return
    }

    val view = LocalView.current
    var screenSize by remember { mutableStateOf(IntSize(1920, 1080)) }

    // Cursor position — start in center
    var cursorX by remember { mutableFloatStateOf(screenSize.width / 2f) }
    var cursorY by remember { mutableFloatStateOf(screenSize.height / 2f) }
    var visible by remember { mutableStateOf(false) }

    val step = 40f  // pixels per D-pad press
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                screenSize = size
                if (!visible) {
                    cursorX = size.width / 2f
                    cursorY = size.height / 2f
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        cursorY = (cursorY - step).coerceAtLeast(0f)
                        visible = true
                        // scroll the fuck up when cursor is in the top 20% of screen
                        if (cursorY < screenSize.height * 0.2f) {
                            coroutineScope.launch { scrollState.scrollBy(-step * 3) }
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        cursorY = (cursorY + step).coerceAtMost(screenSize.height.toFloat())
                        visible = true
                        // scroll the fuck down when cursor is in the bottom 20% of screen
                        if (cursorY > screenSize.height * 0.8f) {
                            coroutineScope.launch { scrollState.scrollBy(step * 3) }
                        }
                        true
                    }
                    Key.DirectionLeft -> { cursorX = (cursorX - step).coerceAtLeast(0f); visible = true; true }
                    Key.DirectionRight -> { cursorX = (cursorX + step).coerceAtMost(screenSize.width.toFloat());  visible = true; true }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (visible) {
                            // inject a tap at cursor position
                            val now = SystemClock.uptimeMillis()
                            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0)
                            val up   = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, cursorX, cursorY, 0)
                            down.source = InputDevice.SOURCE_TOUCHSCREEN
                            up.source   = InputDevice.SOURCE_TOUCHSCREEN
                            view.dispatchTouchEvent(down)
                            view.dispatchTouchEvent(up)
                            down.recycle()
                            up.recycle()
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        content()

        // draw crosshair cursor
        if (visible) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = cursorX
                val y = cursorY
                val armLen  = 18f
                val gapSize = 5f
                val strokeW = 3f
                val shadow  = Color.Black.copy(alpha = 0.6f)
                val white   = Color.White

                // Shadow pass
                for (offset in listOf(Offset(1f, 1f))) {
                    // horizontal arms
                    drawLine(shadow, Offset(x - armLen + offset.x, y + offset.y), Offset(x - gapSize + offset.x, y + offset.y), strokeW + 1f, StrokeCap.Round)
                    drawLine(shadow, Offset(x + gapSize + offset.x, y + offset.y), Offset(x + armLen + offset.x, y + offset.y), strokeW + 1f, StrokeCap.Round)
                    // vertical arms
                    drawLine(shadow, Offset(x + offset.x, y - armLen + offset.y), Offset(x + offset.x, y - gapSize + offset.y), strokeW + 1f, StrokeCap.Round)
                    drawLine(shadow, Offset(x + offset.x, y + gapSize + offset.y), Offset(x + offset.x, y + armLen + offset.y), strokeW + 1f, StrokeCap.Round)
                    // center dot
                    drawCircle(shadow, 2.5f + 1f, Offset(x + offset.x, y + offset.y))
                }

                // White pass
                drawLine(white, Offset(x - armLen, y), Offset(x - gapSize, y), strokeW, StrokeCap.Round)
                drawLine(white, Offset(x + gapSize, y), Offset(x + armLen, y), strokeW, StrokeCap.Round)
                drawLine(white, Offset(x, y - armLen), Offset(x, y - gapSize), strokeW, StrokeCap.Round)
                drawLine(white, Offset(x, y + gapSize), Offset(x, y + armLen), strokeW, StrokeCap.Round)
                drawCircle(white, 2.5f, Offset(x, y))
            }
        }
    }
}

// SurfaceView-backed screensaver prev
@Composable
fun OrbitPreview(
    speed: Int, fps: Int, bgMode: Int,
    bgColorR: Float, bgColorG: Float, bgColorB: Float,
    noGround: Boolean, orbScale: Float, orbCount: Int, cubeChance: Int,
    bgImageUri: String?, cubeUri: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Track the live SurfaceHolder so we can restart when settings change
    var holder by remember { mutableStateOf<SurfaceHolder?>(null) }

    // Push settings + restart render whenever any param changes
    LaunchedEffect(
        speed, fps, bgMode, bgColorR, bgColorG, bgColorB,
        noGround, orbScale, orbCount, cubeChance, bgImageUri, cubeUri
    ) {
        val h = holder ?: return@LaunchedEffect
        OrbitRenderer.nativeStop()
        OrbitRenderer.nativeSetSettings(
            speed = speed,
            fps = fps,
            bgMode = bgMode,
            bgR = bgColorR,
            bgG = bgColorG,
            bgB = bgColorB,
            noGround = noGround,
            orbScale = orbScale,
            orbCount = orbCount,
            cubeChance = cubeChance
        )
        withContext(Dispatchers.IO) {
            // background bitmap
            if (bgMode == OrbitPrefs.BG_IMAGE && bgImageUri != null) {
                try {
                    val uri = android.net.Uri.parse(bgImageUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                        bmp?.let {
                            val argb = if (it.config == android.graphics.Bitmap.Config.ARGB_8888) it
                                       else it.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                            OrbitRenderer.nativeSetBgBitmap(argb)
                        }
                    }
                } catch (_: Exception) {}
            }
            // cube bitmap
            if (cubeUri != null) {
                try {
                    val uri = android.net.Uri.parse(cubeUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                        bmp?.let {
                            val size = minOf(it.width, it.height)
                            val cropped = android.graphics.Bitmap.createScaledBitmap(it, size, size, true)
                            val argb = if (cropped.config == android.graphics.Bitmap.Config.ARGB_8888) cropped
                                       else cropped.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                            OrbitRenderer.nativeSetCubeBitmap(argb)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        OrbitRenderer.nativeStart(h.surface)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).also { sv ->
                OrbitRenderer.nativeSetAssetManager(ctx.assets)
                sv.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) {
                        holder = h
                        scope.launch {
                            OrbitRenderer.nativeSetSettings(
                                speed  = speed,
                                fps = fps,
                                bgMode = bgMode,
                                bgR = bgColorR,
                                bgG = bgColorG,
                                bgB = bgColorB,
                                noGround = noGround,
                                orbScale = orbScale,
                                orbCount = orbCount,
                                cubeChance = cubeChance
                            )
                            withContext(Dispatchers.IO) {
                                if (bgMode == OrbitPrefs.BG_IMAGE && bgImageUri != null) {
                                    try {
                                        val uri = android.net.Uri.parse(bgImageUri)
                                        ctx.contentResolver.openInputStream(uri)?.use { stream ->
                                            val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                                            bmp?.let {
                                                val argb = if (it.config == android.graphics.Bitmap.Config.ARGB_8888) it
                                                           else it.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                                OrbitRenderer.nativeSetBgBitmap(argb)
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                                if (cubeUri != null) {
                                    try {
                                        val uri = android.net.Uri.parse(cubeUri)
                                        ctx.contentResolver.openInputStream(uri)?.use { stream ->
                                            val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                                            bmp?.let {
                                                val size = minOf(it.width, it.height)
                                                val cropped = android.graphics.Bitmap.createScaledBitmap(it, size, size, true)
                                                val argb = if (cropped.config == android.graphics.Bitmap.Config.ARGB_8888) cropped
                                                           else cropped.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                                OrbitRenderer.nativeSetCubeBitmap(argb)
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            OrbitRenderer.nativeStart(h.surface)
                        }
                    }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, he: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) {
                        holder = null
                        OrbitRenderer.nativeSurfaceDestroyed()
                    }
                })
            }
        }
    )

    // Stop the render loop when this composable leaves the composition
    DisposableEffect(Unit) {
        onDispose {
            OrbitRenderer.nativeStop()
            holder = null
        }
    }
}
