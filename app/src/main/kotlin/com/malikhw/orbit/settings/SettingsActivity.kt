package com.malikhw.orbit.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.malikhw.orbit.BuildConfig
import com.malikhw.orbit.update.UpdateChecker
import kotlinx.coroutines.launch

private val ENABLE_UPDATER: Boolean get() = BuildConfig.ENABLE_UPDATER
private val ENABLE_DONATE:  Boolean get() = BuildConfig.ENABLE_DONATE

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
                SettingsScreen(activity = this)
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
fun SettingsScreen(activity: SettingsActivity) {
    val context = LocalContext.current
    val prefs   = remember { OrbitPrefs(context) }

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            SectionCard("Physics") {
                LabeledSlider("Speed: $speed", speed.toFloat(), 1f, 20f) { speed = it.toInt() }
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
                        OutlinedButton(onClick = { pickCubeImage() }) { Text("Browse") }
                        if (cubeUri != null) {
                            OutlinedButton(onClick = { cubeUri = null; prefs.cubeImageUri = null }) { Text("Reset") }
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
                            if (bgImageUri != null)
                                Text(uriFilename(context, bgImageUri!!), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            else
                                Text("None selected", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        OutlinedButton(onClick = { pickBgImage() }) { Text("Browse") }
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
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) { Text(label, fontSize = 12.sp) }
                            }
                        }
                    }

                    if (ENABLE_DONATE) {
                        Spacer(Modifier.height(4.dp))
                        var showDonateDialog by remember { mutableStateOf(false) }
                        Button(
                            onClick = { showDonateDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B35)
                            )
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Donate", fontWeight = FontWeight.Bold)
                        }
                        if (showDonateDialog) {
                            DonationDialog(
                                activity = activity,
                                onDismiss = { showDonateDialog = false }
                            )
                        }
                    }
                }
            }

            // bottom save
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
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
                    } catch (e: Exception) { dreamSettingsError = true }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Android Screensaver Settings", fontSize = 14.sp)
            }
            if (dreamSettingsError) {
                Text("⚠ Couldn't open screensaver settings on this device",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp))
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
    Slider(value = value, onValueChange = onChanged, valueRange = min..max, modifier = Modifier.fillMaxWidth())
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

@Composable
fun DonationDialog(activity: SettingsActivity, onDismiss: () -> Unit) {
    // Only compiled when ENABLE_DONATE == true (playstore flavor).
    // We load DonateHelper via reflection so the main source set never
    // references the billing library directly.
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // State: null = loading, empty = error/none, non-empty = ready
    var products by remember { mutableStateOf<List<Any>?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var helper by remember { mutableStateOf<Any?>(null) }

    DisposableEffect(Unit) {
        val h = try {
            Class.forName("com.malikhw.orbit.billing.DonateHelper")
                .getConstructor(android.content.Context::class.java)
                .newInstance(context)
        } catch (_: Exception) { null }

        helper = h
        if (h != null) {
            scope.launch {
                try {
                    val connectMethod = h.javaClass.getMethod(
                        "connect", kotlinx.coroutines.CoroutineScope::class.java)
                    connectMethod.invoke(h, scope)
                    // poll products flow for up to 5 seconds
                    val productsField = h.javaClass.getDeclaredField("_products").also { it.isAccessible = true }
                    kotlinx.coroutines.delay(300)
                    repeat(17) {
                        val flow = productsField.get(h) as? kotlinx.coroutines.flow.StateFlow<*>
                        val list = flow?.value as? List<*>
                        if (!list.isNullOrEmpty()) {
                            products = list.filterNotNull()
                            return@launch
                        }
                        kotlinx.coroutines.delay(300)
                    }
                    if (products == null) errorMsg = "No donation tiers found.\nPlease set up in-app products\nin the Play Console."
                } catch (e: Exception) {
                    errorMsg = "Billing unavailable"
                }
            }
        } else {
            errorMsg = "Billing not available"
        }

        onDispose {
            scope.launch {
                try {
                    h?.javaClass?.getMethod("disconnect")?.invoke(h)
                } catch (_: Exception) {}
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // header
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint  = Color(0xFFFF6B35),
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    "Support Orbit",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "How much?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(Modifier.height(4.dp))

                when {
                    errorMsg != null -> {
                        Text(
                            errorMsg!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    products == null -> {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Text("Loading tiers…", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    else -> {
                        products!!.forEach { product ->
                            // Extract display name and price via reflection (ProductDetails API)
                            val name  = runCatching {
                                product.javaClass.getMethod("getName").invoke(product) as? String
                            }.getOrNull() ?: "Donation"

                            val price = runCatching {
                                val offerDetails = product.javaClass
                                    .getMethod("getOneTimePurchaseOfferDetails")
                                    .invoke(product)
                                offerDetails?.javaClass
                                    ?.getMethod("getFormattedPrice")
                                    ?.invoke(offerDetails) as? String
                            }.getOrNull() ?: "—"

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val h = helper ?: return@OutlinedButton
                                        val launchMethod = h.javaClass.getMethod(
                                            "launchPurchase",
                                            android.app.Activity::class.java,
                                            Class.forName("com.android.billingclient.api.ProductDetails")
                                        )
                                        launchMethod.invoke(h, activity, product)
                                    } catch (_: Exception) {}
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(12.dp),
                                border   = androidx.compose.foundation.BorderStroke(
                                    1.dp, Color(0xFFFF6B35).copy(alpha = 0.6f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(name,  style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(price, style = MaterialTheme.typography.bodyMedium,
                                        color  = Color(0xFFFF6B35), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Maybe later", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}