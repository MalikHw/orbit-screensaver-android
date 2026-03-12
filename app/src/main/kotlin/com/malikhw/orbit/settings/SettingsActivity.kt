package com.malikhw.orbit.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.malikhw.orbit.update.UpdateChecker
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrbitTheme {
                SettingsScreen(
                    onCheckUpdate = { onCheckUpdate() }
                )
            }
        }
    }

    private fun onCheckUpdate() {
        lifecycleScope.launch {
            UpdateChecker.fetchLatest()
        }
    }
}

// ── Theme ─────────────────────────────────────────────────────────────────────

@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary        = Color(0xFF64B5F6),
            secondary      = Color(0xFF81C784),
            background     = Color(0xFF121212),
            surface        = Color(0xFF1E1E1E),
            onPrimary      = Color.Black,
            onBackground   = Color.White,
            onSurface      = Color.White,
        ),
        content = content
    )
}

// ── Main screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onCheckUpdate: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { OrbitPrefs(context) }

    // ── State mirroring prefs ─────────────────────────────────────────────────
    var speed       by remember { mutableIntStateOf(prefs.speed) }
    var fps         by remember { mutableIntStateOf(prefs.fps) }
    var bgMode      by remember { mutableIntStateOf(prefs.bgMode) }
    var bgColor     by remember { mutableStateOf(
        Color(prefs.bgColorR, prefs.bgColorG, prefs.bgColorB)) }
    var noGround    by remember { mutableStateOf(prefs.noGround) }
    var orbScale    by remember { mutableFloatStateOf(prefs.orbScale) }
    var orbCount    by remember { mutableIntStateOf(prefs.orbCount) }
    var cubeChance  by remember { mutableIntStateOf(prefs.cubeChance) }
    var bgImageUri  by remember { mutableStateOf(prefs.bgImageUri) }
    var cubeUri     by remember { mutableStateOf(prefs.cubeImageUri) }
    var orientation by remember { mutableIntStateOf(prefs.orientation) }

    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var saveToast   by remember { mutableStateOf(false) }

    // ── File pickers ──────────────────────────────────────────────────────────
    val bgImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            bgImageUri = it.toString()
            prefs.bgImageUri = it.toString()
        }
    }
    val cubePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            cubeUri = it.toString()
            prefs.cubeImageUri = it.toString()
        }
    }

    fun save() {
        prefs.speed       = speed
        prefs.fps         = fps
        prefs.bgMode      = bgMode
        prefs.bgColorR    = bgColor.red
        prefs.bgColorG    = bgColor.green
        prefs.bgColorB    = bgColor.blue
        prefs.noGround    = noGround
        prefs.orbScale    = orbScale
        prefs.orbCount    = orbCount
        prefs.cubeChance  = cubeChance
        prefs.orientation = orientation
        saveToast = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Orbit Screensaver", fontWeight = FontWeight.Bold)
                        Text("by MalikHw47",
                            fontSize = 12.sp,
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Display ───────────────────────────────────────────────────────
            SectionCard(title = "Display") {
                Text("Orientation", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OrientationDropdown(
                    selected  = orientation,
                    onSelect  = { orientation = it }
                )
            }

            // ── Physics ───────────────────────────────────────────────────────
            SectionCard(title = "Physics") {
                LabeledSlider("Speed: $speed", speed.toFloat(), 1f, 20f) {
                    speed = it.toInt()
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Infinite fall", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = noGround, onCheckedChange = { noGround = it })
                }
            }

            // ── Orbs ──────────────────────────────────────────────────────────
            SectionCard(title = "Orbs") {
                LabeledSlider(
                    "Count: $orbCount", orbCount.toFloat(), 1f, 300f) {
                    orbCount = it.toInt()
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Low" to 30, "Med" to 80, "High" to 120, "Giga" to 210).forEach { (label, v) ->
                        FilterChip(
                            selected = orbCount == v,
                            onClick  = { orbCount = v },
                            label    = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LabeledSlider(
                    "Size: ${"%.1f".format(orbScale)}×", orbScale, 0.3f, 3.0f) {
                    orbScale = it
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("S" to 0.5f, "M" to 1.0f, "L" to 1.5f, "XL" to 2.0f).forEach { (label, v) ->
                        FilterChip(
                            selected = orbScale == v,
                            onClick  = { orbScale = v },
                            label    = { Text(label) }
                        )
                    }
                }
            }

            // ── Cube ──────────────────────────────────────────────────────────
            SectionCard(title = "Cube") {
                LabeledSlider(
                    "Spawn chance: $cubeChance%", cubeChance.toFloat(), 0f, 100f) {
                    cubeChance = it.toInt()
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom cube image", style = MaterialTheme.typography.bodyMedium)
                        if (cubeUri != null) {
                            Text(
                                uriFilename(context, cubeUri!!),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text("Using bundled default",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { cubePicker.launch("image/*") }) {
                            Text("Browse")
                        }
                        if (cubeUri != null) {
                            OutlinedButton(onClick = {
                                cubeUri = null; prefs.cubeImageUri = null
                            }) { Text("Reset") }
                        }
                    }
                }
            }

            // ── Background ────────────────────────────────────────────────────
            SectionCard(title = "Background") {
                val bgOptions = listOf(
                    "Black" to OrbitPrefs.BG_BLACK,
                    "Color" to OrbitPrefs.BG_COLOR,
                    "Image" to OrbitPrefs.BG_IMAGE
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    bgOptions.forEach { (label, value) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { bgMode = value }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(selected = bgMode == value, onClick = { bgMode = value })
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
                            if (bgImageUri != null) {
                                Text(
                                    uriFilename(context, bgImageUri!!),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text("None selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray)
                            }
                        }
                        OutlinedButton(onClick = { bgImagePicker.launch("image/*") }) {
                            Text("Browse")
                        }
                    }
                }
            }

            // ── Updates ───────────────────────────────────────────────────────
            SectionCard(title = "Updates") {
                val scope = rememberCoroutineScope()
                when (val state = updateState) {
                    is UpdateState.Idle -> {
                        Button(
                            onClick = {
                                updateState = UpdateState.Checking
                                scope.launch {
                                    val info = UpdateChecker.fetchLatest()
                                    updateState = if (info == null) {
                                        UpdateState.Error("Could not reach GitHub")
                                    } else {
                                        UpdateState.Result(info)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Check for updates") }
                    }
                    is UpdateState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
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
                        val appVersion = context.packageManager
                            .getPackageInfo(context.packageName, 0).versionName
                        if (state.info.tag == appVersion || state.info.tag == "v$appVersion") {
                            Text("✓ You're up to date! (${state.info.tag})",
                                color = MaterialTheme.colorScheme.secondary)
                        } else {
                            Text("Update available: ${state.info.tag}",
                                color = Color(0xFFFF9800))
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    UpdateChecker.downloadAndInstall(context, state.info)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Download & Install") }
                        }
                    }
                }
            }

            // ── Links ─────────────────────────────────────────────────────────
            SectionCard(title = "Author") {
                val links = listOf(
                    "Website"  to "https://malikhw.github.io",
                    "YouTube"  to "https://youtube.com/@MalikHw47",
                    "GitHub"   to "https://github.com/MalikHw",
                    "Twitch"   to "https://twitch.tv/MalikHw47",
                    "Discord"  to "https://discord.gg/G9bZ92eg2n",
                    "Ko-fi"    to "https://ko-fi.com/malikhw47",
                    "Throne"   to "https://throne.com/MalikHw47",
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    links.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (label, url) ->
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) { Text(label, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }

            // ── Bottom save button ────────────────────────────────────────────
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Settings", fontSize = 16.sp)
            }

            // ── Open Android screensaver settings ─────────────────────────────
            var dreamSettingsError by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent().apply {
                            setClassName(
                                "com.android.settings",
                                "com.android.settings.Settings\$DreamSettingsActivity"
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        dreamSettingsError = false
                    } catch (e: Exception) {
                        dreamSettingsError = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Android Screensaver Settings", fontSize = 14.sp)
            }
            if (dreamSettingsError) {
                Text(
                    "⚠ Couldn't open screensaver settings on this device",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // Toast-style snackbar
        if (saveToast) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1500)
                saveToast = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 8.dp
                ) {
                    Text(
                        "Saved!",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Orientation dropdown ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrientationDropdown(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        OrbitPrefs.ORIENT_PORTRAIT          to "Portrait (normal)",
        OrbitPrefs.ORIENT_LANDSCAPE         to "Landscape",
        OrbitPrefs.ORIENT_REVERSE_LANDSCAPE to "Landscape (reversed)",
        OrbitPrefs.ORIENT_REVERSE_PORTRAIT  to "Portrait (reversed)",
    )
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == selected }?.second ?: "Portrait (normal)"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    trailingIcon = {
                        if (value == selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style      = MaterialTheme.typography.titleSmall,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, min: Float, max: Float, onChanged: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Slider(
        value         = value,
        onValueChange = onChanged,
        valueRange    = min..max,
        modifier      = Modifier.fillMaxWidth()
    )
}

@Composable
fun ColorPickerRow(color: Color, onColorChange: (Color) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            listOf(
                "R" to color.red,
                "G" to color.green,
                "B" to color.blue
            ).forEach { (ch, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ch, modifier = Modifier.width(16.dp),
                        style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value         = v,
                        onValueChange = { nv ->
                            onColorChange(when (ch) {
                                "R"  -> color.copy(red   = nv)
                                "G"  -> color.copy(green = nv)
                                else -> color.copy(blue  = nv)
                            })
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── Update state ──────────────────────────────────────────────────────────────

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Error(val message: String) : UpdateState()
    data class Result(val info: UpdateChecker.ReleaseInfo) : UpdateState()
}

// ── Util ──────────────────────────────────────────────────────────────────────

fun uriFilename(context: android.content.Context, uriString: String): String {
    return try {
        val uri = Uri.parse(uriString)
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else uriString
        } ?: uriString
    } catch (e: Exception) { uriString }
}
