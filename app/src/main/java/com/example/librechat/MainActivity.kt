package com.example.librechat

import android.Manifest
import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.librechat.ui.ChatScreen
import com.example.librechat.ui.DeviceScreen
import com.example.librechat.ui.LibreChatTheme
import com.example.librechat.ui.NameScreen

/** Android 12 and later need these three granted before any Bluetooth call works. */
private val PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.BLUETOOTH_CONNECT,
)

/** Which screen is showing. */
private sealed class Screen {
    data object Name : Screen()
    data object Devices : Screen()
    data class Chat(val chatId: String, val title: String) : Screen()
}

class MainActivity : ComponentActivity() {

    // Kept here so the Bluetooth radio is released when the app closes.
    private var mesh: MeshManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LibreChatTheme {
                App()
            }
        }
    }

    override fun onDestroy() {
        mesh?.stop()
        mesh = null
        super.onDestroy()
    }

    @Composable
    private fun App() {
        val context = LocalContext.current
        var screen by remember { mutableStateOf<Screen>(Screen.Name) }
        var manager by remember { mutableStateOf<MeshManager?>(null) }
        var name by remember { mutableStateOf("") }

        val askForPermissions = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { answers ->
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            when {
                answers.values.any { granted -> !granted } ->
                    toast("LibreChat cannot work without the Bluetooth permissions")

                adapter == null || !adapter.isEnabled ->
                    toast("Please turn Bluetooth on and try again")

                else -> {
                    val started = MeshManager(context, name)
                    started.start()
                    manager = started
                    mesh = started
                    screen = Screen.Devices
                }
            }
        }

        when (val current = screen) {
            Screen.Name -> NameScreen(
                onStart = { typedName ->
                    name = typedName
                    askForPermissions.launch(PERMISSIONS)
                }
            )

            Screen.Devices -> manager?.let { active ->
                val peers by active.store.peers.collectAsState()
                DeviceScreen(
                    myName = active.myName,
                    myId = active.myId,
                    peers = peers,
                    onOpenChat = { chatId, title -> screen = Screen.Chat(chatId, title) },
                )
            }

            is Screen.Chat -> manager?.let { active ->
                BackHandler { screen = Screen.Devices }
                val messages by active.store.messages(current.chatId).collectAsState()
                ChatScreen(
                    title = current.title,
                    messages = messages,
                    onSend = { text -> active.send(current.chatId, text) },
                    onBack = { screen = Screen.Devices },
                )
            }
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
