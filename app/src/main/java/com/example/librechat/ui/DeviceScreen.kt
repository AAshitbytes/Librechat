package com.example.librechat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.librechat.PUBLIC
import com.example.librechat.Peer

/**
 * The main screen: the public chat, and every phone the app knows about.
 *
 * "Nearby" means there is a direct Bluetooth link. "In mesh" means the phone is further away and
 * its messages reach us through other phones.
 */
@Composable
fun DeviceScreen(
    myName: String,
    myId: String,
    peers: List<Peer>,
    onOpenChat: (chatId: String, title: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("LibreChat", style = MaterialTheme.typography.headlineSmall)
        Text(
            "You are $myName (#$myId)",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenChat(PUBLIC, "Public chat") },
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Public chat", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Everybody in the mesh can read this",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Devices", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (peers.isEmpty()) {
            Text(
                "Looking for other phones running LibreChat...",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn {
            items(peers) { peer ->
                PeerRow(peer, onClick = { onOpenChat(peer.id, peer.name) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PeerRow(peer: Peer, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(peer.name, style = MaterialTheme.typography.bodyLarge)
            Text("#${peer.id}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (peer.nearby) "Nearby" else "In mesh",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
