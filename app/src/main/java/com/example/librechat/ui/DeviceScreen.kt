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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.librechat.PUBLIC
import com.example.librechat.Peer

@Composable
fun DeviceScreen(
    myName: String,
    myId: String,
    pairedPeers: List<Peer>,
    discoveredPeers: List<Peer>,
    unreadChatIds: Set<String>,
    onOpenChat: (chatId: String, title: String) -> Unit,
    onRefresh: () -> Unit,
    onNameChanged: (String) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    var showNameDialog by remember { mutableStateOf(false) }
    
    val filteredPaired = pairedPeers.filter { it.name.contains(filter, ignoreCase = true) }
    val filteredDiscovered = discoveredPeers.filter { it.name.contains(filter, ignoreCase = true) }

    if (showNameDialog) {
        NameEditDialog(
            currentName = myName,
            onDismiss = { showNameDialog = false },
            onConfirm = {
                onNameChanged(it)
                showNameDialog = false
            }
        )
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
            Text("LibreChat", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("You are $myName (#$myId)", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { showNameDialog = true }) {
                Text("Change Name")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenChat(PUBLIC, "Public chat") },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Public chat", style = MaterialTheme.typography.titleMedium)
                    if (PUBLIC in unreadChatIds) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Everybody in the mesh can read this",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search contacts or devices...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (filteredPaired.isNotEmpty()) {
                item {
                    Text("My Contacts", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(filteredPaired) { peer ->
                    PeerRow(peer, unreadChatIds, onOpenChat)
                    HorizontalDivider()
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            item {
                Text("Nearby Devices", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (filteredDiscovered.isEmpty()) {
                item {
                    Text(
                        if (filter.isEmpty()) "Looking for other phones..." else "No other devices found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredDiscovered) { peer ->
                    PeerRow(peer, unreadChatIds, onOpenChat)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PeerRow(
    peer: Peer,
    unreadChatIds: Set<String>,
    onOpenChat: (chatId: String, title: String) -> Unit
) {
    val isOnline = peer.lastSeen > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenChat(peer.id, peer.name) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                peer.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isOnline) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (peer.id in unreadChatIds) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Text(
            when {
                peer.nearby -> "Direct"
                isOnline -> "Relay"
                else -> "Offline"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun NameEditDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
