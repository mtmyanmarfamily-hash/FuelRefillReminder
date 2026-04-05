# ⛽ Petrol Quota MM

Android app for Myanmar car owners to track their government-issued fuel quota.

## Features

- **Quota Setup** – Enter your vehicle name and total litre quota per 7-day window
- **Refill Tracking** – Record each refill (up to **2 times** per 7-day window)
- **Live Status** – See used / remaining litres and refills at a glance
- **Smart Reminders** – Evening notification at **7:00 PM** the day before your window resets, only if you still have quota/refills remaining
- **Auto-stop** – Reminders stop automatically once both refills are used or quota is exhausted
- **Chat Q&A** – Ask "When can I refuel?", "How much is left?", "How many days until reset?" and get instant answers

## Quota Rules

| Rule | Value |
|------|-------|
| Max refills per 7-day window | **2** |
| Window duration | **7 days** from first refill |
| Quota type | User-defined litres (e.g. 30 L) |
| Reminder time | **7:00 PM** evening before reset |

## Build

[![Build APK](https://github.com/YOUR_USERNAME/MyanmarPetrolReminder/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_USERNAME/MyanmarPetrolReminder/actions/workflows/build.yml)

### Via GitHub Actions (recommended)
1. Push this repo to GitHub
2. Go to **Actions** tab → **Build APK** → **Run workflow**
3. Download `PetrolQuotaMM-debug.apk` from Artifacts

### Local build
```bash
chmod +x gradlew
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
Requires: JDK 17, Android SDK

## Install
Enable **Install from unknown sources** on your Android phone, then open the APK file.

## Minimum Requirements
- Android 8.0 (API 26) or higher
