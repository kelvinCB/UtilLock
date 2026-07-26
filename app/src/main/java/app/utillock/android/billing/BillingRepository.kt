package app.utillock.android.billing

import android.app.Activity
import android.app.Application
import app.utillock.android.BuildConfig
import app.utillock.android.data.BackendClient
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BillingState(
    val connected: Boolean = false,
    val product: ProductDetails? = null,
    val premium: Boolean = false,
    val message: String? = null,
)

class BillingRepository(
    application: Application,
    private val backend: BackendClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = mutableState

    private val billingClient = BillingClient.newBuilder(application)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.orEmpty().forEach(::processPurchase)
            } else {
                mutableState.value = mutableState.value.copy(message = result.debugMessage)
            }
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if (billingClient.isReady) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ready = result.responseCode == BillingClient.BillingResponseCode.OK
                mutableState.value = mutableState.value.copy(connected = ready, message = result.debugMessage.takeIf { !ready })
                if (ready) {
                    queryProduct()
                }
            }

            override fun onBillingServiceDisconnected() {
                mutableState.value = mutableState.value.copy(connected = false)
            }
        })
    }

    fun launchPurchase(activity: Activity) {
        val product = mutableState.value.product ?: return
        val offer = product.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken
            ?: return
        val details = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offer)
            .build()
        billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(details)).build(),
        )
    }

    fun restore() {
        if (!billingClient.isReady) return
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.forEach(::processPurchase)
        }
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(BuildConfig.PLAY_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build(),
        ) { result, queryResult ->
            mutableState.value = mutableState.value.copy(
                product = queryResult.productDetailsList.firstOrNull(),
                message = result.debugMessage.takeIf { result.responseCode != BillingClient.BillingResponseCode.OK },
            )
        }
    }

    private fun processPurchase(purchase: com.android.billingclient.api.Purchase) {
        if (purchase.purchaseState != com.android.billingclient.api.Purchase.PurchaseState.PURCHASED) return
        scope.launch {
            val active = backend.verifyPurchase(purchase.purchaseToken, BuildConfig.PLAY_PRODUCT_ID)
            mutableState.value = mutableState.value.copy(premium = active)
            if (active && !purchase.isAcknowledged) {
                billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
                ) { }
            }
        }
    }
}
