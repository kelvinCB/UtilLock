package app.utillock.android.data

import android.app.Application
import app.utillock.android.billing.BillingRepository

class AppContainer(application: Application) {
    val protectionRepository = ProtectionRepository(application)
    val sessionRepository = SessionRepository(application)
    val backendClient = BackendClient(sessionRepository)
    val billingRepository = BillingRepository(application, backendClient)
}

