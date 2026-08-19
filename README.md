# Survivor.io Rainbow Mine Monitor

Android floating-window monitor for the Survivor.io Rainbow Mine event. It receives board updates from a PCAPdroid-MITM addon and paints the board over the game.

## What is included

- `SurvivorRainbowMineBoard.apk` — Android monitor application.
- `naval_live_addon.py` — PCAPdroid-MITM addon.
- `android_naval_monitor/` — Kotlin source project.

The addon sends compact JSON updates to the Android app through UDP `127.0.0.1:8086`. It does not modify the game or send game actions.

## Requirements

Install on the Android device:

1. Survivor.io from the normal store installation.
2. [PCAPdroid](https://play.google.com/store/apps/details?id=com.emanuelef.remote_capture).
3. [PCAPdroid-mitm](https://github.com/emanuelef/PCAPdroid-mitm/releases).
4. `SurvivorRainbowMineBoard.apk` from this repository.

The Survivor.io package used by the filters is `com.dxx.firenow`.

## Complete setup

### 1. Install and configure the monitor app

1. Install `SurvivorRainbowMineBoard.apk`.
2. Open it and grant **Display over other apps** permission.
3. Leave the UDP port set to `8086`.
4. Tap **START MONITOR**. Keep the floating window visible while playing.

### 2. Install the PCAPdroid-MITM addon

Copy `naval_live_addon.py` to this folder on the Android device:

```text
/Downloads/PCAPdroid_addons/
```

In PCAPdroid-MITM, open the user-addon screen, select that file, and enable/reload it. If the file picker displays shared storage, choose `Download/PCAPdroid_addons/` (Android may show `Download` instead of `/Downloads`).

Do not rename the file. The addon must be loaded by PCAPdroid-MITM; simply storing it on the device is not enough.

### 3. Configure PCAPdroid

1. Open PCAPdroid.
2. In **Select application**, choose **Survivor.io** (`com.dxx.firenow`).
3. In **Decryption rules**, add the same Survivor.io application. If rules can be entered by hostname, also add `prod-game.survivorio.com`.
4. Enable **TLS decryption / PCAPdroid-MITM**.
5. Install and trust the PCAPdroid CA certificate when Android asks.
6. Set **Block QUIC** to **Always** so the game uses decryptable TLS/TCP traffic.
7. Start the capture.

### 4. Capture the board

Use this order:

```text
SurvivorRainbowMineBoard → START MONITOR
PCAPdroid-MITM → addon enabled
PCAPdroid → capture started
Survivor.io → open Rainbow Mine
```

When the game downloads a new board or registers a selection, the overlay updates automatically. Orange cells are unrevealed pieces, green cells are discovered pieces, blue cells are selected empty cells, and dark cells are still unknown.

## Troubleshooting

- **No floating window:** grant the overlay permission and press **START MONITOR** again.
- **Window is visible but never updates:** confirm that the monitor is listening on UDP `8086`, and that the addon is enabled in PCAPdroid-MITM.
- **TLS packets remain encrypted:** reinstall/enable the PCAPdroid CA certificate, keep TLS decryption enabled, and set Block QUIC to **Always**.
- **No game traffic:** verify that the selected app is `com.dxx.firenow`, not PCAPdroid itself.
- **Addon errors after obfuscation:** use the plain `naval_live_addon.py`. Python `marshal`/native runtimes are tied to the Python version and Android ABI used by PCAPdroid-MITM.
- **New board is not shown:** stop and restart the monitor, reload the addon, and start a fresh capture.

## Development

The Android source is in `android_naval_monitor/`. The app listens for JSON messages containing `type`, `board_number`, `rows`, `cols`, `matrix`, and `selected`. The addon extracts Survivor.io Dxx messages `19702`, `19709`, and `19710` and converts them to that JSON format.

## Disclaimer

This project is a local monitoring/debugging tool. Use it only with traffic and devices you are authorized to inspect, and comply with the game's terms of service.
