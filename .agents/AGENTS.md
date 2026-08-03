# Git & Deployment (GitHub-Push)
Nach jeder Änderung am Programm (sobald Code verändert, hinzugefügt oder korrigiert wurde) müssen die Änderungen zwingend committet und auf GitHub in den remote Branch (z. B. `main`) gepusht werden.

**Vorgaben für den Ablauf:**
1. Nach Code-Änderungen immer ein erfolgreiches Build (`npm run build`) verifizieren.
2. Alle geänderten Dateien stagen (`git add .` oder gezielte Auswahl).
3. Einen aussagekräftigen Commit-Text verfassen.
4. Sofort `git push` ausführen, um das automatische Cloudflare Pages Deployment anzustoßen, damit die Änderungen live gehen.
