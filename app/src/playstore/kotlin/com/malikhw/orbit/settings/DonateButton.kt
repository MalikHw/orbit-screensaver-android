package com.malikhw.orbit.settings

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.malikhw.orbit.billing.DonateHelper
import kotlinx.coroutines.launch

@Composable
fun DonateButton(activity: Activity) {
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth().tvFocusBorder(RoundedCornerShape(50)),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
    ) {
        Icon(Icons.Default.Favorite, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Donate", fontWeight = FontWeight.Bold)
    }

    if (showDialog) {
        DonationDialog(activity = activity, onDismiss = { showDialog = false })
    }
}

@Composable
private fun DonationDialog(activity: Activity, onDismiss: () -> Unit) {
    val scope  = rememberCoroutineScope()
    val helper = remember { DonateHelper(activity) }

    val products by helper.products.collectAsState()
    val state    by helper.state.collectAsState()

    DisposableEffect(Unit) {
        helper.connect(scope)
        onDispose { helper.disconnect() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape     = RoundedCornerShape(20.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier             = Modifier.padding(24.dp),
                horizontalAlignment  = Alignment.CenterHorizontally,
                verticalArrangement  = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint     = Color(0xFFFF6B35),
                    modifier = Modifier.size(36.dp)
                )
                Text("Support Orbit",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("How much?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray)

                Spacer(Modifier.height(4.dp))

                when {
                    state is DonateHelper.State.Error -> {
                        Text(
                            (state as DonateHelper.State.Error).message,
                            color     = MaterialTheme.colorScheme.error,
                            style     = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    products.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Text("Loading tiers…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray)
                    }
                    else -> {
                        products.forEach { product ->
                            val name  = product.name
                            val price = product.oneTimePurchaseOfferDetails?.formattedPrice ?: "—"

                            OutlinedButton(
                                onClick = {
                                    helper.launchPurchase(activity, product)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(12.dp),
                                border   = BorderStroke(1.dp, Color(0xFFFF6B35).copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
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