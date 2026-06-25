# Badini Translate – Android App

A dictionary-based translation app for **Kurdish (Badini)** ↔ **German / English / Turkish**.

Built with Jetpack Compose + Room database. Designed to be tested with the **Test Android Apps** plugin (`android-emulator-qa`, `android-performance`).

---

## Build & Install

### Voraussetzungen
- Android Studio Ladybug (2024.3+) oder SDK 35 + JDK 17
- Emulator (API 26+) oder physisches Gerät

### Bauen
```bash
./gradlew :app:assembleDebug
```

### Installieren (via adb)
```bash
# Emulator-Serial ermitteln
adb devices

# App installieren
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk

# Activity auflösen & starten
adb -s <serial> shell cmd package resolve-activity --brief com.badini.translate
adb -s <serial> shell am start -n com.badini.translate/.MainActivity
```

---

## Testen mit dem "Test Android Apps" Plugin

### 1. UI-Flow testen (`android-emulator-qa`)

**App starten & Screenshot:**
```bash
# App starten
adb shell am start -n com.badini.translate/.MainActivity

# Screenshot machen
adb exec-out screencap -p > /tmp/badini-home.png
```

**Text eingeben & Übersetzen:**
```bash
# Fokussieren & Text eingeben (koordinatenabhängig – vorher UI-Tree dumpen)
adb exec-out uiautomator dump /dev/tty > /tmp/ui.xml

# Aus dem UI-Tree die Koordinaten des Textfelds holen (via ui_pick.py):
python3 path/to/ui_pick.py /tmp/ui.xml "Enter text"

# Text eingeben
adb shell input tap <x> <y>
adb shell input text "ez"

# Translate-Button antippen
python3 path/to/ui_pick.py /tmp/ui.xml "Translate"
adb shell input tap <x> <y>

# Ergebnis prüfen
adb exec-out screencap -p > /tmp/badini-result.png
```

**Logs prüfen:**
```bash
adb logcat -c
# Flow ausführen...
adb logcat -d | grep -i "badini\|translate\|room"
```

### 2. Performance analysieren (`android-performance`)

**Startup-Trace (Perfetto):**
```bash
adb shell perfetto -o /data/misc/perfetto-traces/startup.pftrace \
  --txt -c - <<EOF
duration_ms: 10000
buffers: { size_kb: 65536 }
data_sources: { config { name: "android.surfaceflinger.frametimeline" } }
data_sources: { config { name: "android.graphics.frametimeline" } }
data_sources: { config { name: "linux.sched" } }
EOF

# App kalt starten
adb shell am start -n com.badini.translate/.MainActivity
```

**Jank/Frame-Drops messen:**
```bash
adb shell dumpsys gfxinfo com.badini.translate reset
# App benutzen (scrollen, übersetzen)
adb shell dumpsys gfxinfo com.badini.translate > /tmp/gfxinfo.txt
```

**Memory-Check:**
```bash
adb shell dumpsys meminfo com.badini.translate
```

---

## Projektstruktur

```
BadiniTranslate/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── assets/dictionary.json     ← Wörterbuch als JSON
│       ├── java/com/badini/translate/
│       │   ├── MainActivity.kt
│       │   ├── BadiniTranslateApp.kt
│       │   ├── translation/
│       │   │   ├── Language.kt
│       │   │   ├── TranslationResult.kt
│       │   │   └── TranslationEngine.kt
│       │   ├── data/
│       │   │   ├── db/
│       │   │   │   ├── DictionaryEntry.kt
│       │   │   │   ├── DictionaryDao.kt
│       │   │   │   └── AppDatabase.kt
│       │   │   └── repository/
│       │   │       └── DictionaryRepository.kt
│       │   └── ui/
│       │       ├── theme/
│       │       └── screen/
│       │           └── TranslateScreen.kt
│       └── res/values/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/wrapper/
```

---

## Wörterbuch erweitern

1. **`dictionary.json`** bearbeiten – Format:
   ```json
   {
     "source_lang": "badini",
     "source_word": "Beispiel",
     "target_lang": "de",
     "target_word": "Übersetzung",
     "part_of_speech": "noun",
     "usage_example": ""
   }
   ```
2. App neu bauen & installieren – beim nächsten Start wird das Dictionary automatisch importiert.

**Hinweis:** Die App importiert das Dictionary nur einmal (bei erstem Start oder wenn die DB leer ist). Bei Aktualisierung: App-Daten löschen (`adb shell pm clear com.badini.translate`) oder `version` in `AppDatabase.kt` erhöhen.

---

## Features (MVP)

- [x] Wörterbuch-basierte Übersetzung (Badini ↔ DE/EN/TR)
- [x] Wort-für-Wort mit Erkennung unbekannter Wörter
- [x] Übersetzung kopieren
- [x] Sprachauswahl (Dropdown)
- [ ] Sprachrichtung umkehren (Swap)
- [ ] Autocomplete / Suchvorschläge
- [ ] Favoriten / Verlauf
- [ ] Text-to-Speech
- [ ] Offline-Sprachpakete

---

## Lizenz

Privat – entwickelt von TAHA SARDAR
