package app.utillock.android.model

/** UtilLock must remain reachable while another app is being blocked. */
const val UTILLOCK_PACKAGE_NAME = "app.utillock.android"

fun Set<String>.excludingUtilLock(currentPackageName: String = UTILLOCK_PACKAGE_NAME): Set<String> =
    filterNot { it == UTILLOCK_PACKAGE_NAME || it == currentPackageName }.toSet()
