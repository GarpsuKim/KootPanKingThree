# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**KootPanKingThree** is a Windows desktop application that displays a 3D analog clock in the system tray and enables remote PC control via Telegram Bot — no VPN or port forwarding required. It integrates Google Calendar, Naver Calendar, Gmail, YouTube/CCTV streaming, webcam capture, and global system hooks (keyboard/mouse lock screen).

Language: **Java 17+**, UI: **JavaFX 21.0.10**, Build: **Windows BAT scripts + jpackage**

## Build & Run

### Requirements
- JDK 17+ with `javac` and `jpackage` in PATH
- Source in `3D_SOURCE/`, dependencies in `3D_Lib/`, compiled classes go to `3D_Class/`

### Build native EXE (main release build)
```batch
cd 3D_BAT
@ReleaseBLD_All(app-image).bat
```
Output: `3D_Dist/KootPanKingThree/KootPanKingThree.exe`

The build script: kills any running instance → cleans build dirs → compiles all `.java` files with UTF-8 encoding → creates JAR → runs `jpackage` → copies JavaFX DLLs into output.

### Compile only (no packaging)
```batch
cd 3D_BAT
@all.Bat
```
This runs all `0Com_*.BAT` scripts sequentially to compile individual classes.

### Run from compiled classes (with console output)
```batch
cd 3D_BAT
9ComRun_KootPanKingThreeLaunch.BAT
```

### Manual compile command pattern
```batch
javac --module-path 3D_Lib\javafx-sdk-21.0.10\lib ^
      --add-modules javafx.controls,javafx.graphics,javafx.base,javafx.swing,javafx.fxml,javafx.web,javafx.media ^
      -cp "3D_Lib\*;3D_Lib\javafx-sdk-21.0.10\lib\*" ^
      -encoding UTF-8 -d 3D_Class ^
      3D_SOURCE\*.java
```

**No unit test framework** — testing is manual via GUI interaction.

## Architecture

### Entry Point & Wiring
`KootPanKingThreeLaunch` (extends JavaFX `Application`) is the entry point. It wires all subsystems together: initializes `AppContext` (config/settings), starts `TelegramBot`, sets up global system hooks, and hands off to `MainWindow` and `FxGPUNeon` for the UI.

### Key Source Files (`3D_SOURCE/`)

| File | Role |
|------|------|
| `KootPanKingThreeLaunch.java` | JavaFX `Application` entry point, subsystem wiring |
| `KootPanKingThreeApp.java` | UI styling, Mac-style context menus |
| `FxGPUNeon.java` (~248KB) | 3D analog clock engine (SceneAssembler, OverlayRenderer, MenuController, SetupPanelController) |
| `MainWindow.java` (~265KB) | Settings tabbed pane, context menus, all configuration UI |
| `TelegramBot.java` (~145KB) | Telegram polling bot, command dispatch, camera handler interface |
| `TOOLS.java` (~81KB) | Screen capture, webcam (JNA/DirectShow), AliveStatusAgent |
| `AppContext.java` | Centralized config singleton, settings INI read/write |
| `GoogleCalendarService.java` | Google Calendar OAuth + event queries |
| `NaverCalendarService.java` | Naver Calendar API |
| `GmailSender.java` | Gmail OAuth, HTML email sending |
| `Multimedia.java` | YouTube/CCTV/local video playback in 3D bezel |
| `BackgroundPlayer.java` | Audio playback, chimes, fade transitions |
| `ChimeController.java` | Hourly chime scheduling, alarms |
| `ItsCctvManager.java` | IP camera management |
| `Kakao.java` | KakaoTalk message mirroring |

### Subsystem Communication Pattern
Subsystems communicate via **callback interfaces** injected at startup:
- `TelegramBot.CommandHandler` — implemented by `MainWindow`, receives bot commands (screen capture, PC info, calendar queries, shutdown)
- `TelegramBot.CameraHandler` — optionally injected, handles `/cam`, `/rec`, `/camHello`, `/camstop` commands
- `FxGPUNeon` exposes methods that `MainWindow` calls for theme changes, city clock updates, video background switching

### Configuration
All user settings are stored in `%APPDATA%\KootPanKingThree\clock_settings.ini` — the app downloads a default config from GitHub if missing. `AppContext` is the singleton gateway for reading/writing these settings. The INI file stores: clock size/position/theme, digital clock format, Telegram token & Chat ID, Google OAuth tokens, YouTube/CCTV URLs, camera settings, and auto-run registry flag.

### 3D Clock Engine (`FxGPUNeon`)
Internal structure uses nested controller classes:
- `ClockController` — scene lifecycle, JavaFX animation timer
- `SceneAssembler` — builds 3D geometry (bezel, hands, markers)
- `OverlayRenderer` — 2D text overlays on top of 3D scene (digital time, city names, date)
- `MenuController` — right-click context menu (theme, clock mode, video background)
- `SetupPanelController` — settings panel with live preview

### Telegram Bot Security
All commands are restricted to the authorized Chat ID stored in config. Destructive commands (`/down`, `/reboot`) require a confirmation reply. The bot polls `getUpdates` every ~2-3 seconds on a background thread.

### Build Artifacts
- `3D_Class/` — compiled `.class` files (cleaned on each build)
- `3D_Dist/KootPanKingThree/` — final app-image bundle (~250-400 MB including bundled JDK)
- `ERROR_OF_COMPILE/build_log.txt` — compilation errors
- `ERROR_OF_COMPILE/jpackage_Out.txt` — jpackage output

### Source Variants
- `3D_SOURCE/` — **active development** (Korean + English mixed)
- `3D_SOURCE_한국어_최종버젼/` — Korean-only backup
- `3D_SOURCE_영어전환_1차/` — English translation work-in-progress

All active changes go in `3D_SOURCE/`. The other two directories are reference/backup only.
