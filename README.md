# Survivor.io Rainbow Mine Monitor

Android floating-window monitor for the Survivor.io Rainbow Mine event. It receives board updates from PCAPdroid-MITM and paints the board over the game.

## What is included

- `SurvivorRainbowMineBoard.apk` — Android monitor application.
- `naval_live_addon.py` — PCAPdroid-MITM addon.
- `android_naval_monitor/` — Kotlin source project.

The addon sends compact JSON updates to the Android app through UDP `127.0.0.1:8086`. It does not modify the game or send game actions.

## Requirements

Install on the Android device:

1. Survivor.io from the normal store installation.
2. [PCAPdroid](https://play.google.com/store/apps/details?id=com.emanuelef.remote_capture).
3. `SurvivorRainbowMineBoard.apk` from this repository.

The Survivor.io package used by the filters is `com.dxx.firenow`.

## Complete setup

### 1. Configure PCAPdroid and PCAPdroid-MITM

1. Open **PCAPdroid**.
2. In **Select application**, choose **Survivor.io** (`com.dxx.firenow`).
3. Enable **TLS decryption / PCAPdroid-MITM**.
4. If this is the first run, follow the prompts inside PCAPdroid-MITM to download and install the PCAPdroid-MITM component and the CA certificate.
5. Accept the Android prompts to install and trust the certificate.
6. Set **Block QUIC** to **Always** so the game uses decryptable TLS/TCP traffic.

### 2. Install and enable the addon

Copy `naval_live_addon.py` to this folder on the Android device:

```text
/Downloads/PCAPdroid_addons/
```

If the file picker displays shared storage, choose `Download/PCAPdroid_addons/` (Android may show `Download` instead of `/Downloads`).

In the PCAPdroid-MITM addon screen, select `naval_live_addon.py`, enable it, and reload the addon. Do not rename the file. The addon must be loaded by PCAPdroid-MITM; simply storing it on the device is not enough.

### 3. Install and configure the monitor app

1. Install `SurvivorRainbowMineBoard.apk`.
2. Open it and grant **Display over other apps** permission.
3. Leave the UDP port set to `8086`.
4. Tap **START MONITOR**. Keep the floating window visible while playing.

### 4. Capture the board

Use this order:

```text
PCAPdroid → TLS decryption/PCAPdroid-MITM configured
PCAPdroid-MITM → addon enabled
SurvivorRainbowMineBoard → START MONITOR
PCAPdroid → start capture
Survivor.io → open Rainbow Mine
```

When the game downloads a new board or registers a selection, the overlay updates automatically. Orange cells are unrevealed pieces, green cells are discovered pieces, blue cells are selected empty cells, and dark cells are still unknown.

## Troubleshooting

- **No floating window:** grant the overlay permission and press **START MONITOR** again.
- **Window is visible but never updates:** confirm that the monitor is listening on UDP `8086`, and that the addon is enabled in PCAPdroid-MITM.
- **TLS packets remain encrypted:** repeat the PCAPdroid-MITM certificate installation, keep TLS decryption enabled, and set Block QUIC to **Always**.
- **No game traffic:** verify that the selected app is `com.dxx.firenow`, not PCAPdroid itself.
- **Addon errors after obfuscation:** use the plain `naval_live_addon.py`. Python `marshal`/native runtimes are tied to the Python version and Android ABI used by PCAPdroid-MITM.
- **New board is not shown:** stop and restart the monitor, reload the addon, and start a fresh capture.

## Disclaimer

This project is a local monitoring/debugging tool. Use it only with traffic and devices you are authorized to inspect, and comply with the game's terms of service.
