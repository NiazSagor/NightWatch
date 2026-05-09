# NightWatch: Adaptive Alarm PoC

> **⚠️ R&D PHASE NOTICE:** This project is currently in the Research & Development phase. The core logic for sleep detection and alarm shifting is implemented as a Proof of Concept (PoC). It is **not yet a fully functional or production-ready application**. Use for testing and architectural evaluation only.

## Overview
NightWatch is an "Adaptive Alarm" system designed to protect your sleep health. Instead of a rigid wake-up time, it detects when you actually fall asleep and shifts your morning alarm forward to ensure you always hit your **Target Sleep Duration**, while respecting a **Hard Deadline** for mandatory wake-ups.

## Core Workflow
1. **Initial Setup:** User sets a Target Wake-up (e.g., 07:00), Required Sleep (e.g., 7 hours), and a Hard Deadline (e.g., 09:00).
2. **Sleep Monitoring:** The app leverages the **Google Sleep API** to monitor sleep confidence in the background.
3. **The "Shift" Trigger:** Once 2 consecutive events with >85% confidence are detected:
    - Calculates the "Sleep Delay" (Actual Start vs. Ideal Start).
    - Reschedules the system alarm using `AlarmManager`.
    - Caps the new alarm time to the "Hard Deadline."
4. **Auto-Stop Mechanism:** To ensure **zero battery drain**, the app immediately unsubscribes from the Sleep API and terminates all background monitoring once the alarm is shifted.

## Technical Architecture
- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Background Work:** Google Play Services (Activity Recognition) + BroadcastReceivers
- **Storage:** SharedPreferences (for PoC state persistence)
- **Alarm Engine:** `AlarmManager` with Exact Alarm permissions

## Zero-Battery-Drain Design
Unlike typical sleep trackers, NightWatch does not run a foreground service all night.
- It hands off monitoring to **Google Play Services** via a `PendingIntent`.
- The app process is **killed** while waiting for sleep.
- The system wakes the app only for a few milliseconds when a sleep event is detected.
- Once the alarm is rescheduled, the app unsubscribes and returns to a completely dormant state until the alarm rings.

## Current Limitations (R&D Phase)
- **Sleep API Latency:** The Google Sleep API typically delivers events in 10-minute windows; immediate "falling asleep" detection is subject to system delivery speeds.
- **Physical Device Required:** Activity Recognition (Sleep API) does not work reliably on standard Android Emulators.
- **UI/UX:** The current UI is a functional skeleton for configuration and does not include an active "Alarm Ringing" screen (currently uses Toasts and Logs).
- **Hard Deadline Safety:** In this PoC, if the user falls asleep so late that the shift exceeds the Hard Deadline, the alarm is capped at the deadline regardless of sleep duration.

## Setup & Installation
1. Ensure you have **Google Play Services** installed on your testing device.
2. Grant **Physical Activity** (Activity Recognition) and **Notification** permissions when prompted.
3. Build using Android Studio or `./gradlew assembleDebug`.

## Permissions Used
- `ACTIVITY_RECOGNITION`: Access to Sleep API.
- `SCHEDULE_EXACT_ALARM`: For precise wake-up timing.
- `POST_NOTIFICATIONS`: For status updates on Android 13+.
- `RECEIVE_BOOT_COMPLETED`: (Planned) To reschedule monitoring after a device restart.
