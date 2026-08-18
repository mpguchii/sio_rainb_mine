SurvivorNavalMonitor
====================

1. Ensure mitmproxy is installed and `mitmdump` is available in PATH.
2. Install/configure your own mitmproxy CA certificate in Google Play Games.
3. Run SurvivorNavalMonitor.exe.
4. Click "Iniciar captura" before opening Survivor.io.
5. Click "Parar captura" when finished.

Captures and .naval_live.json state files are saved in the `captures` folder
beside this executable. Do not distribute your mitmproxy private key or CA
certificate; each user must configure their own certificate.
