package pl.medidesk.mobile.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.medidesk.mobile.core.model.SyncState
import pl.medidesk.mobile.core.model.SyncStatus
import pl.medidesk.mobile.core.ui.theme.MdOrange
import pl.medidesk.mobile.core.ui.theme.MdRed

@Composable
fun SyncStatusBar(syncState: SyncState, isOffline: Boolean) {
    val show = isOffline || syncState.totalPending > 0 || syncState.status == SyncStatus.ERROR

    AnimatedVisibility(visible = show) {
        val bgColor = when {
            isOffline -> MdRed
            syncState.status == SyncStatus.ERROR -> MdRed
            syncState.totalPending > 0 -> MdOrange
            else -> MdOrange
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.Sync,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isOffline && syncState.totalPending > 0 ->
                            "Offline — ${syncState.totalPending} oczekujących"
                        isOffline -> "Tryb offline"
                        syncState.status == SyncStatus.SYNCING -> "Synchronizacja..."
                        syncState.totalPending > 0 ->
                            "${syncState.totalPending} oczekujących na synchronizację"
                        syncState.status == SyncStatus.ERROR ->
                            "Błąd synchronizacji — ${syncState.errorMessage}"
                        else -> ""
                    },
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
