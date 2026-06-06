# Localization Editor SC2 KSP

Localization Editor SC2 KSP is a desktop editor for StarCraft II map and mod localization. It helps map makers open SC2 archives, compare language columns, translate missing text, apply SC2 glossary terminology, and save changes back into the correct `.SC2Data/LocalizedData` structure.

It is built for real SC2 projects, not only single text files:
- Opens `GameStrings.txt`, `ObjectStrings.txt`, `TriggerStrings.txt`, `GameHotkeys.txt`, and custom localization `.txt` files.
- Works with `SC2Map`, `SC2Mod`, and `mpq` archives.
- Lets you switch between localization files inside the same archive without reopening the map.
- Supports single-target translation or translating from one source language to all supported languages.
- Uses editable StarCraft II glossaries so unit, ability, button, and common SC2 terms stay consistent.
- Supports Google Translate Free, Google Cloud, DeepL, Gemini, SiliconFlow, Cloudflare Worker AI, and local LibreTranslate.

## Releases
- https://github.com/VoVanRusLvSC2/Localization-Editor-SC2-KSP/releases

## Current Installer
- Current Windows installer build: `2.1`
- If an older build is installed, use the latest installer from Releases.

## Quick Start
1. Download the latest release.
2. Install and launch the editor.
3. Open your `SC2Map` / `SC2Mod` / `mpq` archive or a localization `.txt` file.
4. If an archive contains several localization files, use the archive-file dropdown near the file name to switch between them.
5. Select a source language, target language, and translation backend.
6. Translate, review the table, then save.

## Logs
Main application logs are rotating UTF-8 files:
- Windows: `![alt text](image.png)` through `app-4.log`
- Other OS: `~/.Localization_Editor_SC2_KSP/logs/app-0.log` through `app-4.log`

Local LibreTranslate startup and repair logs are stored separately:
- Windows: `%LOCALAPPDATA%\LocalizationEditorSC2KSP\argos-runtime\startup-logs\lt-*.log`
- Other OS: `~/.localization-editor-argos-runtime/startup-logs/lt-*.log`

## Editable Glossaries After Install
The installer places editable glossary files in the application folder:

- `glossary` next to `Localization Editor SC2 KSP.exe`
- example: `C:\Program Files\Localization Editor SC2 KSP\glossary`

You can edit these files after installation without rebuilding the app.

Default files placed there:
- `sc2_word_glossary_KSP.txt`
- `Addition_UnitNames_Detailed_KSP.txt`
- `Addition_Weapons_Detailed_KSP.txt`
- `Addition_Abilities_Detailed_KSP.txt`

On startup, the editor loads glossary files from that install-folder `glossary` first when they exist. For legacy installs, `%LOCALAPPDATA%\Localization Editor SC2 KSP\glossary` is still used as a fallback if present.

## 2.1 Notes
- Added archive file switching for common localization files inside one opened SC2 archive.
- DeepL now auto-detects Free vs Pro API endpoints and uses DeepL-compatible language codes.
- AI prompt backends now receive stronger SC2 glossary hints and preserve frozen glossary placeholders.
- Main word glossary now includes `Creep -> Слизь` and `Nydus -> Нидус`.
- Logging locations are documented.

## Dependencies
- Java: `JDK 17` (required)
- Build tool: `Maven` (for source build)
- OS: Windows desktop environment (primary target)

Main Maven dependencies used by the app:
- JavaFX: `javafx-controls`, `javafx-fxml`, `javafx-media` (`22.0.1`)
- HTTP/API: `retrofit2`, `okhttp logging-interceptor`
- JSON/Parsing: `gson`, `jackson`, `jsoup`
- Language detection: `langdetect`, `tika-core`
- Archive and native integration: `jmpq3`, `commons-compress`, `jna`, `jna-platform`

Optional local translation backend dependencies:
- Python `3.10+`
- `libretranslate`
- `minisbd` (if startup reports `download_models` import error, upgrade it)

## Translation Backends and AI Info
For backend details, pricing notes, and setup hints (Google, DeepL, Cloudflare, SiliconFlow, LibreTranslate, etc.), see:
- `README_TRANSLATE.txt`

## Optional: Local LibreTranslate
If you want to use LibreTranslate locally:

```bash
python -m pip install --upgrade pip
python -m pip install libretranslate
python -m libretranslate.main --host 127.0.0.1 --port 5000 --disable-web-ui
```

If startup fails with:
`ImportError: cannot import name 'download_models' from 'minisbd'`

run:

```bash
python -m pip install --upgrade minisbd
python -m libretranslate.main --host 127.0.0.1 --port 5000 --disable-web-ui
```

Health check:
- `http://127.0.0.1:5000/languages`

The app expects LibreTranslate at:
- `http://127.0.0.1:5000`
