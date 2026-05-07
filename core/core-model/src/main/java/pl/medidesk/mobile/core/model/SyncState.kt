package pl.medidesk.mobile.core.model

data class SyncState(
    val status: SyncStatus = SyncStatus.IDLE,
    val lastSyncedAt: String? = null,
    val pendingCheckins: Int = 0,
    val pendingWalkins: Int = 0,
    val errorMessage: String? = null
) {
    val totalPending: Int get() = pendingCheckins + pendingWalkins
}

enum class SyncStatus {
    IDLE, SYNCING, SUCCESS, ERROR
}
