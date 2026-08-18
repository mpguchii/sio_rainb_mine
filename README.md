# Survivor.io — Rainbow Mine Board

An Android floating overlay board helper for the **Survivor.io — Rainbow Mine** event. It receives real-time event grid state from PCAPdroid and displays the mine grid on a floating overlay window over the game.

**Powered by MP**

---

## 📱 Required App Downloads

1. **PCAPdroid (Network Capture)**:
   * Download from Google Play Store: [PCAPdroid on Play Store](https://play.google.com/store/apps/details?id=com.emanuelef.remote_packet_capture)
2. **PCAPdroid-mitm Addon (TLS Decryption Engine)**:
   * Download the APK from GitHub: [PCAPdroid-mitm Releases](https://github.com/emanuele-f/PCAPdroid-mitm/releases)
3. **Survivor.io (Patched APK)**:
   * The Survivor.io APK must be patched with `apk-mitm` (or custom network security config) to allow user-installed CA certificates.

---

## 🛠️ Step-by-Step Setup Guide

### Step 1: Install PCAPdroid & PCAPdroid-mitm Addon
1. Install **PCAPdroid** from the Google Play Store.
2. Install **PCAPdroid-mitm** APK.
3. Open PCAPdroid, go to **Settings -> Traffic Inspection**, select **TLS Decryption (mitmproxy)**, and follow the prompt to install the PCAPdroid CA Certificate into Android User Certificates.

### Step 2: Install the Python Addon Script
1. Copy the protected addon script `naval_live_addon.py` and the `pyarmor_runtime_000000` folder into the PCAPdroid-mitm user addons directory on your Android device:
   ```
   /sdcard/Android/data/com.emanuelef.remote_packet_capture.mitm/files/addons/
   ```
   *(Or select "Add user addon" inside PCAPdroid-mitm settings and pick `naval_live_addon.py`)*.
2. In PCAPdroid, verify that the **Survivor.io Addon** is enabled.

### Step 3: PCAPdroid Configuration
Configure PCAPdroid with the following settings:
* **Target App**: Select **Survivor.io** (`com.habby.survivorio`).
* **Traffic Inspection**: Set to **TLS Decryption (mitmproxy)**.
* **Block QUIC Traffic**: **Enable** (Check "Block QUIC" under PCAPdroid Settings so the game falls back from UDP/QUIC to TLS/TCP).
* **Dump Mode**: None (or HTTP Exporter if saving captures).

### Step 4: Run the Rainbow Mine Board App
1. Open **Survivor.io — Rainbow Mine Board**.
2. Tap **Grant Permission** to enable the Floating Window (System Alert Window).
3. Tap **START MONITOR** (Listens on UDP port `8086`).
4. Start capture in **PCAPdroid**.
5. Launch **Survivor.io** and open the Rainbow Mine event. The floating overlay board will automatically appear and reveal the event grid!

---

## 📂 Repository Contents

* `SurvivorRainbowMineBoard.apk`: Compiled Android app APK (~5.78 MB).
* `naval_live_addon.py`: Protected Python addon for PCAPdroid-mitm.
* `pyarmor_runtime_000000/`: Native ARM64 runtime library for the protected addon.
* `android_naval_monitor/`: Full Kotlin Android project source code.

---

*Powered by MP*
