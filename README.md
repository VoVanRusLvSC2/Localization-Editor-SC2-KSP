# Localization-Editor-SC2-KSP
A localization editor for StarCraft II maps and mods. Opens GameStrings.txt files, translates them into selected languages, and automatically saves them into the correct SC2Data directory structure.
---

## Requirements

### 1. Java (JDK 17+)

Check your Java version:

```bash
java -version
```

---

### 2. Maven (3.8+ recommended)

Check your Maven version:

```bash
mvn -v
```

---

### 3. Python (Optional – only for auto-translation)

Required only if you want to use LibreTranslate.

Install Python 3.9+ from:  
https://www.python.org/downloads/

Install LibreTranslate:

```bash
pip install libretranslate
```

Start the LibreTranslate server:

```bash
python -m libretranslate --host 127.0.0.1 --port 5000
```

The application expects LibreTranslate running at:

```
http://127.0.0.1:5000
```

---

## Run with Maven

From the project root directory (where `pom.xml` is located), run:

```bash
mvn clean javafx:run
```

If needed:

```bash
mvn exec:java -Dexec.mainClass=lv.lenc.AppLauncher
```

---

## Build (Optional)

To compile the project:

```bash
mvn clean package
```
