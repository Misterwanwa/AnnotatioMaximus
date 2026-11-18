# Git & Deployment (GitHub-Push)
Nach jeder Änderung am Programm (sobald Code verändert, hinzugefügt oder korrigiert wurde) müssen die Änderungen zwingend committet und auf GitHub in den remote Branch (z. B. `main`) gepusht werden.

**Vorgaben für den Ablauf:**
1. Nach Code-Änderungen immer ein erfolgreiches Build verifizieren (`cmd /c run-tablet.cmd -BuildOnly` bzw. Gradle `assembleDebug` / Tests).
2. Alle geänderten Dateien stagen (`git add .` oder gezielte Auswahl).
3. Einen aussagekräftigen Commit-Text verfassen.
4. Sofort `git push` ausführen, um die Änderungen auf GitHub zu sichern.

# Tablet-Deployment & Benutzerhinweis (nach jeder Codeänderung)
Damit der Nutzer die App nach Code-Änderungen ohne Android Studio direkt testen kann (und die Schritte nicht im Kopf behalten muss), gilt nach **jeder** Änderung am App-Code oder an Ressourcen zusätzlich:

1. **Automatisches Tablet-Deployment versuchen:**
   - Prüfen, ob ein autorisiertes Android-Gerät per ADB erreichbar ist (`C:\Users\kevin\AppData\Local\Android\Sdk\platform-tools\adb.exe devices`).
   - **Wenn ein Gerät bereit (`device`) ist:** Automatisch `cmd /c run-tablet.cmd` ausführen (baut die Debug-APK, installiert sie per `installDebug` und startet `com.annotatio.maximus/.MainActivity` auf dem Tablet). Bei mehreren Geräten nur dann automatisch ausführen, wenn ein einzelnes physisches Tablet eindeutig erkennbar ist, sonst per `-Serial <ID>` bzw. Hinweis fragen.
2. **Immer auf die 1-Klick-Bedienung hinweisen (auch wenn kein Tablet angeschlossen war):**
   - Wenn kein Gerät angeschlossen oder nicht autorisiert ist: Build mit `cmd /c run-tablet.cmd -BuildOnly` verifizieren und den Nutzer in der Abschlussantwort **immer** aktiv daran erinnern, wie er die neue Version mit einem Klick selbst auf das Tablet bringt:
     - **In VS Code:** `Strg + Umschalt + B` (*„Auf Tablet bauen & starten“*) bzw. Task *„Nur APK auf Tablet installieren (ohne Build)“*
     - **Im Windows Explorer:** Doppelklick auf `run-tablet.cmd`
     - **Tablet-Voraussetzung:** USB-Kabel (USB-Debugging aktiv & bestätigt) oder WLAN-ADB verbunden.
