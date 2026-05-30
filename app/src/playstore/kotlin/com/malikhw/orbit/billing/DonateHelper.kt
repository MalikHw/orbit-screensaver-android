package com.malikhw.orbit.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wraps Google Play Billing for the donation flow.
 *
 * Product IDs must be set up in the Play Console as one-time in-app products.
 * Suggested IDs: "donation_small", "donation_medium", "donation_large"
 *
 * Call [connect] once (e.g. in onCreate), [disconnect] when done (e.g. onDestroy).
 * Then call [fetchProducts] to populate [products], and [launchPurchase] to buy one.
 * fml
 */
class DonateHelper(private val context: Context) {

    companion object {
        // These must match the product IDs created in the Play Console
        val PRODUCT_IDS = listOf("donation_small", "donation_medium", "donation_large")
    }

    sealed class State {
        object Disconnected : State()
        object Connecting   : State()
        object Ready        : State()
        data class Error(val message: String) : State()
    }

    private val _state    = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products

    private var billingClient: BillingClient? = null

    // connection

    fun connect(scope: CoroutineScope) {
        _state.value = State.Connecting
        billingClient = BillingClient.newBuilder(context)
            .setListener { result, purchases ->
                // Handle purchase updates (acknowledgement)
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases?.forEach { purchase ->
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                            && !purchase.isAcknowledged) {
                            scope.launch {
                                val ackParams = AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.purchaseToken)
                                    .build()
                                billingClient?.acknowledgePurchase(ackParams)
                            }
                        }
                    }
                }
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

        billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = State.Ready
                    scope.launch { fetchProducts() }
                } else {
                    _state.value = State.Error("Billing unavailable (${result.responseCode})")
                }
            }
            override fun onBillingServiceDisconnected() {
                _state.value = State.Disconnected
            }
        })
    }

    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
        _state.value = State.Disconnected
    }
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    // product fetch
    suspend fun fetchProducts() {
        val client = billingClient ?: return
        if (_state.value !is State.Ready) return

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PRODUCT_IDS.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()

        val result = withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<Pair<BillingResult, List<ProductDetails>?>> { cont ->
                client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                    cont.resume(Pair(billingResult, productDetailsList)) {}
                }
            }
        }

        if (result.first.responseCode == BillingClient.BillingResponseCode.OK) {
            // Sort by price ascending so cheapest is first
            _products.value = (result.second ?: emptyList())
                .sortedBy { it.oneTimePurchaseOfferDetails?.priceAmountMicros ?: Long.MAX_VALUE }
        } else {
            _state.value = State.Error("Could not load donation tiers")
        }
    }

    // purchase
    fun launchPurchase(activity: Activity, product: ProductDetails): BillingResult {
        val client = billingClient ?: return BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
            .build()

        val productList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .build()
        )
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productList)
            .build()

        return client.launchBillingFlow(activity, flowParams)
    }
}