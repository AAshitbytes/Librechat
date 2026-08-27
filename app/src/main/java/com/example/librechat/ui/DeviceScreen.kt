package com.example.librechat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    unreadChatIds: Set<String>,
    onOpenChat: (chatId: String, title: String) -> Unit,
    onRefresh: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredPeers = remember(peers, searchQuery) {
        if (searchQuery.isBlank()) {
            peers
        } else {
            peers.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.id.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("LibreChat", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
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
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Public chat", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Everybody in the mesh can read this",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (unreadChatIds.contains(PUBLIC)) Dot()
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search devices...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))

        Text("Devices", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (filteredPeers.isEmpty()) {
            Text(
                if (searchQuery.isEmpty()) "Looking for other phones running LibreChat..."
                else "No devices match \"$searchQuery\"",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn {
            items(filteredPeers) { peer ->
                PeerRow(
                    peer = peer,
                    hasUnread = unreadChatIds.contains(peer.id),
                    onClick = { onOpenChat(peer.id, peer.name) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PeerRow(peer: Peer, hasUnread: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasUnread) {
                Dot()
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text(peer.name, style = MaterialTheme.typography.bodyLarge)
                Text("#${peer.id}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            if (peer.nearby) "Nearby" else "In mesh",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(Color.Red, CircleShape)
    )
}
