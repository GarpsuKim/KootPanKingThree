[🇰🇷 Korean](README_KO.MD) | 🇺🇸 English

Youtube  :  https://www.youtube.com/@garpsu.smartkim4069/featured

<img width="473" height="991" alt="image" src="https://github.com/user-attachments/assets/b1d03d3d-b77c-45b0-b202-efd2f8ab44b6" />   <img width="485" height="998" alt="image" src="https://github.com/user-attachments/assets/f65289b7-5c9a-4c5e-8cee-09bd3c967ec4" />

# 🕐 KootPanKingThree — Server/PC Remote Control via Telegram ( + Analog Clock )

> Control your PC from anywhere through any router —
> no port forwarding, no VPN, just Telegram.

![Platform](https://img.shields.io/badge/Platform-Windows%2010%2F11-blue)
![Java](https://img.shields.io/badge/Java-17%2B-orange)
![License](https://img.shields.io/badge/License-MIT-green)

---

## 🚀 What is KootPanKingThree?

KootPanKingThree is a **[Windows desktop app]** that doubles as a **[full remote control system via Telegram bot].**

It stays running in the system tray — so your Telegram bot is always alive, always ready — with a 3D analog clock persistently displayed on your monitor.

---

## ✨ Key Features

### 🤖 Telegram Remote Control
| Command | Description |
|---------|-------------|
| `/capture` | Capture clock screen → send to Telegram |
| `/s` | Full screen capture → send to Telegram |
| `/c1`~`/c4` | Capture specific monitor (1~4) |
| `/cmd` | Execute DOS/CMD commands remotely |
| `/ps` | Execute PowerShell commands remotely |
| `/down` | Shutdown PC remotely (with confirmation) |
| `/reboot` | Reboot PC remotely (with confirmation) |
| `/wh` | Get PC info (IP, OS, username) |
| `/text` | Send text → auto-save to PC |
| `/save` | Save files to PC remotely |
| `/ms` | Query Google Calendar schedule |
| `/ns` | Query Naver Calendar schedule |
| `/h` | Show command list |

### 🕐 Analog Clock
- Beautiful analog clock always visible in system tray
- Always-on-top display option
- World clock support (15 cities)
- Chime & alarm system

### 📡 Integrations
- Google Calendar
- Naver Calendar
- Gmail
- KakaoTalk message mirroring
- CCTV / YouTube live background feed
- Windows auto-start
- Auto-update system

---

## 💡 Why Telegram?

Most remote control tools require:
| Others | KootPanKingThree |
|--------|-------------|
| ❌ Port forwarding | ✅ Zero network config |
| ❌ VPN setup | ✅ Works behind any router |
| ❌ Static IP required | ✅ Works from anywhere |
| ❌ Paid subscription | ✅ Free (Telegram is free) |

KootPanKingThree uses Telegram bot as a secure relay —
no server, no cloud, your PC talks directly to your phone.

---

## 📦 Installation

1. Download `KootPanKingThree.zip` from [Releases](../../releases)
2. **Before extracting**: Right-click ZIP → Properties →
   Check **"Unblock"** → Apply
3. Extract and run `KootPanKingThree.exe`

> ⚠️ Windows SmartScreen may warn on first run.
> Click **"More info"** → **"Run anyway"**
> Source code is fully open — build it yourself if unsure.

---

## 🔨 Build from Source

```bash
# Requirements: JDK 17+
# Clone this repository, then:

_ReleaseBLD_All.bat

# Output: dist\KootPanKingThree\KootPanKingThree.exe
```

---

## 🤖 Telegram Bot Setup (5 minutes)

1. Open Telegram → search **`@BotFather`**
2. Send `/newbot` → follow instructions → **copy the token**
3. Send any message to your new bot
4. Visit `https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates`
5. Find your **Chat ID** in the response
6. Enter token + Chat ID in KootPanKing settings → Done ✅

---

## 🛡️ Security

- All commands are restricted to your Chat ID only
- Dangerous commands (`/down`, `/reboot`) require confirmation
- Source code is fully open for inspection
- No data is sent to any third-party server

---

## 📬 Contact

- 📧 Email: garpsu@naver.com
- 📝 Blog: https://blog.naver.com/garpsu
- 

---

## ⭐ If this helped you, please give it a Star!

It helps others discover this project.
