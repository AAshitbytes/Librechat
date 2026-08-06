package com.example.librechat

import java.util.UUID

/**
 * The Bluetooth ids the app uses. Every phone running LibreChat advertises SERVICE_UUID, which is
 * how the app recognises another copy of itself among all the Bluetooth devices around.
 *
 * These two ids were made up for this project. CCCD_UUID is a standard one defined by Bluetooth
 * itself and is used to switch notifications on.
 */
val SERVICE_UUID: UUID = UUID.fromString("f1e2d3c4-b5a6-4978-8a9b-0c1d2e3f4a5b")
val CHAR_UUID: UUID = UUID.fromString("f1e2d3c4-b5a6-4978-8a9b-0c1d2e3f4a5c")
val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/** Used for all log messages, so `adb logcat -s LibreChat` shows what the mesh is doing. */
const val TAG = "LibreChat"
