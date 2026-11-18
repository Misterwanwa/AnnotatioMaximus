# AnnotatioMaximus

## 1-Klick auf dem Tablet testen (ohne Android Studio)

1. Tablet per **USB-Kabel** (mit aktiviertem *USB-Debugging*) oder per **WLAN-ADB** (`adb connect <IP>:<Port>`) verbinden.
2. **Entweder in VS Code:** `Strg + Umschalt + B` drücken (*„Auf Tablet bauen & starten“*).
3. **Oder im Windows Explorer:** Doppelklick auf `run-tablet.cmd`.

Das Skript `run-tablet.ps1` baut die Debug-APK automatisch mit Gradle, installiert sie auf dem Tablet und startet die App direkt.

**Hilfreiche Parameter für `run-tablet.ps1` / `run-tablet.cmd`:**
- `-SkipBuild` – installiert nur die zuletzt gebaute APK ohne neuen Gradle-Build (in VS Code als Task *„Nur APK auf Tablet installieren (ohne Build)“*).
- `-BuildOnly` – baut nur die Debug-APK (`assembleDebug`), ohne dass ein Tablet angeschlossen sein muss.
- `-Serial <ID>` – wählt gezielt ein Gerät aus, falls Tablet und Emulator gleichzeitig verbunden sind.