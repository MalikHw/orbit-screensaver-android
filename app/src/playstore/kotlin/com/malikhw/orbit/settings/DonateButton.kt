package com.malikhw.orbit.settings

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import com.malikhw.orbit.R
import com.malikhw.orbit.billing.DonateHelper
import kotlinx.coroutines.delay

@Composable
fun DonateButton(activity: Activity) {
    val context = LocalContext.current
    val prefs = remember { OrbitPrefs(context) }
    var showDialog by remember { mutableStateOf(false) }
    var showWToast by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth().tvFocusBorder(RoundedCornerShape(50)),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
    ) {
        Icon(Icons.Default.Favorite, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.btn_donate), fontWeight = FontWeight.Bold)
    }

    if (showDialog) {
        DonationDialog(
            activity = activity,
            onDismiss = { showDialog = false },
            onSuccess = {
                prefs.hasDonated = true
                showDialog = false
                showWToast = true
            }
        )
    }
    if (showWToast) {
        LaunchedEffect(Unit) {
            delay(2500)
            showWToast = false
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFF6B35),
                tonalElevation = 8.dp
            ) {
                Text(
                    stringResource(R.string.toast_w_bro),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun DonationDialog(activity: Activity, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val scope = rememberCoroutineScope()
    val helper = remember {
        DonateHelper(activity, onPurchaseSuccess = onSuccess)
    }

    val products by helper.products.collectAsState()
    val state by helper.state.collectAsState()

    // track whether the user tapped a tier so we can show a waiting indicator
    var purchasing by remember { mutableStateOf(false) }
    var showPublishingInfo by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        helper.connect(scope)
        onDispose { helper.disconnect() }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(onClick = { showPublishingInfo = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.cd_info),
                            tint = Color.Gray
                        )
                    }
                }
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF6B35),
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    stringResource(R.string.donate_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                when {
                    purchasing -> {
                        // waiting for Google Play to come back
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.donate_waiting_payment),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    state is DonateHelper.State.Error -> {
                        Text(
                            (state as DonateHelper.State.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    products.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.donate_loading_tiers),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    else -> {
                        Text(
                            stringResource(R.string.donate_how_much),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(4.dp))
                        products.forEach { product ->
                            val name = product.name
                            val price = product.oneTimePurchaseOfferDetails?.formattedPrice ?: "—"

                            OutlinedButton(
                                onClick = {
                                    purchasing = true
                                    helper.launchPurchase(activity, product)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF6B35).copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        price, style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFFF6B35), fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.donate_maybe_later), color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }

    if (showPublishingInfo) {
        AlertDialog(
            onDismissRequest = { showPublishingInfo = false },
            confirmButton = {
                TextButton(onClick = { showPublishingInfo = false }) {
                    Text(stringResource(R.string.btn_ok))
                }
            },
            text = {
                Text(
                    stringResource(R.string.donate_publishing_info_desc)
                )
            }
        )
    }
}
