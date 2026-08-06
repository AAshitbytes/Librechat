# LibreChat

A chat app for Android that works without internet, mobile data or a server. Phones talk to each
other directly over Bluetooth Low Energy and pass messages along for one another, so two phones can
chat even when they are too far apart to reach each other, as long as somebody is standing in
between.

There are two kinds of chat:

- **Public chat** — everybody in the mesh receives the message.
- **One to one chat** — the message is addressed to a single phone. Other phones still carry it,
  but only the phone it is addressed to shows it.

## How it works

### Every phone is both a server and a client

Bluetooth Low Energy normally has one device advertising (the *peripheral*) and another one
connecting to it (the *central*). A mesh needs every phone to do both at the same time, so the app
runs two halves:

| Half | File | What it does |
| --- | --- | --- |
| Peripheral | `BleServer.kt` | Advertises the app's service id so others can find this phone, and runs a GATT server with one characteristic |
| Central | `BleClient.kt` | Scans for that same service id and connects to whatever it finds |

Because both run together, phone A can be connected to B as a client while B is connected to C as a
client, and so on. That chain of links is the mesh.

Both halves use the same single characteristic to move data in both directions:

- the central **writes** to the characteristic to send,
- the peripheral **notifies** on the characteristic to send back.

When a link is made the app asks for a larger packet size (`requestMtu(517)`), so a whole message
fits into one write instead of being split up.

### Packets

Everything sent over a link is a small JSON object. There are only two kinds.

A `hello`, sent once when a link comes up, so the two phones learn each other's name. It is never
passed on:

```json
{"type":"hello","from":"7f3a","name":"Prem"}
```

A `msg`, which is a chat message and does travel across the mesh:

```json
{"type":"msg","id":"a1b2c3d4","from":"7f3a","name":"Prem","to":"","text":"hello","ttl":5}
```

`to` is empty for the public chat, or the id of one phone for a private message. `id` is a random
value used to spot messages that have already been handled, and `ttl` is the number of hops the
message is still allowed to travel.

### Hop to hop

The routing rule is *flooding*: send everything you receive to everybody you are connected to. On
its own that would go round in circles forever, so two things stop it, both in `MeshRouter.kt`:

1. **Duplicate filtering.** Each phone remembers the ids of the last 200 messages it handled. A
   message whose id is already in that list is thrown away instead of being passed on again.
2. **Hop limit.** Each phone takes one off the `ttl` before passing a message on. At zero the
   message stops, so a message cannot travel more than five hops.

So the full rule for an arriving message is:

```
if the id was seen before        -> drop it
if it is public or addressed to me -> show it
if ttl is still above 1          -> take one off and send it to every link except the one it came from
```

The result is that A → B → C works: B shows the message and also forwards it to C, even though A
and C cannot hear each other.

`MeshRouter` deliberately contains no Android code, which is why the mesh rules can be unit tested
on a normal computer.

### Finding devices

The device list separates two cases:

- **Nearby** — there is a direct Bluetooth link to that phone. Learned from its `hello`.
- **In mesh** — no direct link, but one of its messages reached us through other phones.

This means a distant phone appears in the list once it has said something in the public chat.

## Project layout

```
app/src/main/java/com/example/librechat/
    MainActivity.kt      asks for permissions, decides which screen shows
    MeshManager.kt       joins the two Bluetooth halves to the router
    MeshRouter.kt        the relay rules: duplicates, hop limit, addressing
    Packet.kt            the JSON format sent over a link
    ChatStore.kt         messages and known devices, held in memory
    Ble.kt               the Bluetooth ids the app uses
    BleServer.kt         advertising and GATT server (peripheral half)
    BleClient.kt         scanning and GATT client (central half)
    ui/NameScreen.kt     type your name
    ui/DeviceScreen.kt   public chat and the list of devices
    ui/ChatScreen.kt     messages and the text box
    ui/Theme.kt          Material colours

app/src/test/java/com/example/librechat/
    PacketTest.kt        the JSON format
    MeshRouterTest.kt    the relay rules
```

## Built with

- Kotlin
- Jetpack Compose with Material 3 for the screens
- The Android Bluetooth Low Energy APIs, used directly, with no Bluetooth library
- Kotlin coroutines and `StateFlow` to let the screens follow the data
- `org.json`, which Android already includes
- Gradle with the Kotlin DSL
- JUnit for the unit tests

Minimum Android version is 12 (API 31), which is where the Bluetooth permissions were split into
`BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT`. `BLUETOOTH_SCAN` is declared with
`neverForLocation`, so the app does not need the location permission.

## Building and installing

Open the project in Android Studio and press Run, or from a terminal:

```
./gradlew assembleDebug
./gradlew installDebug        # with a phone plugged in and USB debugging on
```

Run the unit tests with:

```
./gradlew test
```

The app cannot be tested on an emulator, because Android emulators have no real Bluetooth radio.
At least two physical phones are needed.

## Trying the mesh

1. Install the app on three phones and turn Bluetooth on.
2. Open the app on each one, type a name and press Start.
3. With all three close together, every phone should list the other two as **Nearby**. Send
   something in the public chat and it appears everywhere.
4. To see relaying, move phone A and phone C far apart, or put a wall between them, keeping phone B
   in the middle. A and C now list each other as **In mesh** rather than Nearby, and messages still
   get through, because B is passing them on.
5. `adb logcat -s LibreChat` shows links coming up and going down while this happens.

## What this version does not do

These were left out to keep the code short and readable:

- **No encryption.** A private message is only addressed to one phone; the phones relaying it could
  read it if they were modified to do so.
- **Foreground only.** The mesh stops when the app is closed, as there is no background service.
- **No history.** Messages are kept in memory, so they are gone when the app restarts.
- **Flooding does not scale.** Every message reaches every phone, which is fine for a room but
  would be wasteful for a very large network.
- **No delivery guarantee.** A message sent while a phone is out of range is simply missed; there
  is no store and forward for phones that are not connected yet.
