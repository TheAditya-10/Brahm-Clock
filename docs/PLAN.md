# Brahm Clock — Product & Technical Plan

A lightweight Android clock app with every standard clock feature, plus one
headline feature: an **adaptive Brahma Muhurta alarm** that follows the sunrise
at your location every single day and wakes you gently, in stages, across the
48‑minute Brahma Muhurta window.

---

## 1. What is Brahma Muhurta (and the exact math)

In the Vedic/Ayurvedic time system a day is divided into **muhurtas of 48
minutes** each. Brahma Muhurta is the **second‑to‑last muhurta of the night**:

```
   ← 96 min before sunrise        48 min before sunrise →        sunrise
   |============ Brahma Muhurta ============|------ last muhurta ------|☀
   start = sunrise − 96 min                end = sunrise − 48 min
```

> Decision: the traditional **96 min** (2 muhurtas × 48 min) is the default;
> the offset is adjustable in the Brahma alarm settings (72–120 min).

Because sunrise shifts every day (and differs by city), the alarm time must be
**recomputed daily** from latitude/longitude and date.

---

## 2. Sunrise data: external API vs. offline calculation

| Option | Pros | Cons |
|---|---|---|
| **Offline NOAA solar algorithm** (recommended, primary) | No internet, no API key, zero latency, works on airplane mode, ~150 lines of Kotlin, accurate to ~1 min | Need to implement/test once |
| [sunrise-sunset.org API](https://sunrise-sunset.org/api) (`api.sunrise-sunset.org/json?lat=..&lng=..&date=..&formatted=0`) | Free, no key, simple JSON | Needs internet; alarm must not depend on network at 4 AM |
| Open‑Meteo (`api.open-meteo.com/v1/forecast?...&daily=sunrise`) | Free, no key, 16‑day forecast | Same network dependency |

**Decision:** compute sunrise **on‑device** with the NOAA algorithm. No
external API is *required*. Optionally use sunrise‑sunset.org once in debug
builds / unit tests to cross‑validate our numbers.

**Location:** Android's built‑in `LocationManager` (coarse location,
`ACCESS_COARSE_LOCATION`) — no Google Play Services needed, which keeps the APK
small and works on de‑Googled phones. City accuracy (~1–3 km) is plenty:
sunrise changes ~4 s per km east‑west. Fallbacks:
1. Last known location (cached)
2. Manual city search (bundled small offline city list, ~1k cities, lat/lng/tz)
3. Manual lat/lng entry

---

## 3. The adaptive "staged" wake‑up (core feature)

Instead of one loud alarm, the app plays **gentle pulses** that grow through
the 48‑minute window until you wake up and dismiss.

Default schedule (user‑configurable), with `S` = window start:

| Stage | Time | What happens |
|---|---|---|
| 1 | S + 0 min | Soft bell / tanpura, 15 s, 10% volume, no vibration |
| 2 | S + 8 min | 20 s, 20% volume |
| 3 | S + 16 min | 25 s, 35% volume, light vibration |
| 4 | S + 24 min | 30 s, 50% volume |
| 5 | S + 32 min | 40 s, 65% volume, vibration |
| 6 | S + 40 min | 60 s, 80% volume |
| 7 | S + 48 min (window end) | **Full alarm**, rings until dismissed (100%, normal alarm behaviour) |

Rules:
- Each pulse fades in (volume ramp) and fades out automatically — no tap needed.
- **"I'm awake"** on any pulse cancels all remaining stages for today.
- **Snooze** skips to the next stage.
- Modes: *Gentle* (above), *Standard* (single alarm at window start), *Custom*
  (choose stage count, intervals, sounds).
- Sound: the phone's default alarm, any system ringtone, or **any audio file
  from the user's own library** (Storage Access Framework, persisted
  permission). No bundled sounds in v1, which keeps the APK small.
- Repeat days (e.g. Mon–Sat) and "skip next" just like a normal alarm.

### Sleep reminder
A notification at **bedtime = window start − sleep goal** (default 8 h),
optionally 15/30/60 min early. E.g. window 04:58 → reminder 20:58. It follows
the same repeat days and skip as the alarm.

### Rescheduling logic
Schedule only the **next** occurrence; recompute after it fires and on these
triggers:
- Alarm finished/dismissed → compute tomorrow's window
- `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` broadcasts
- Significant location change (> 5 km, checked whenever the app opens)
- Settings changed

Edge cases: polar regions where the sun doesn't rise/set → fall back to the
user's fixed backup time and show a notice; DST transitions handled by using
`java.time.ZonedDateTime` everywhere.

---

## 4. Tech stack (light by design)

| Layer | Choice | Why |
|---|---|---|
| Language | **Kotlin** | Standard for Android |
| UI | **Jetpack Compose + Material 3** | Modern, less code; with R8 the release APK stays ~4–6 MB |
| Min / target SDK | minSdk 26 (Android 8), targetSdk 35 | Covers ~95% devices, `java.time` available natively |
| Storage | **SharedPreferences** + `org.json` | A few dozen rows; no database or annotation processing needed |
| Scheduling | `AlarmManager.setAlarmClock()` | Exact, survives Doze, shows alarm icon in status bar |
| Background refresh | None: recomputed after every ring, on boot/time change, on app open | One fewer library |
| Ringing | Foreground service + `MediaPlayer` + full‑screen intent notification | Reliable lock‑screen alarm |
| Location | Platform `LocationManager` | No Play Services dependency |
| Sun math | Own NOAA implementation (pure Kotlin, unit tested) | No network, no library |
| DI | Manual (simple `AppContainer`) | Hilt is overkill for this size |
| Networking | **None** in v1 | Lighter and private |

Permissions: `USE_EXACT_ALARM` (allowed for alarm‑clock apps),
`POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `ACCESS_COARSE_LOCATION`,
`RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`.
No internet permission in v1.

---

## 5. App layout (screens & wireframes)

Bottom navigation with 5 tabs. **Brahma** is the home tab.

```
[ 🌅 Brahma ] [ ⏰ Alarm ] [ 🕐 Clock ] [ ⏳ Timer ] [ ⏱ Stopwatch ]
```

Language: English only.

Visual style: dark‑first theme (it's used at 4 AM), deep indigo night sky →
saffron/amber dawn gradient, large clean numerals, AMOLED black option.

### 5.1 Brahma (home)
```
┌──────────────────────────────────┐
│  Brahm Clock              ⚙      │
│  📍 Indore, IN  · Sun, 26 Oct    │
│                                  │
│        ╭──────────────────╮      │
│       ╱   night ─── dawn   ╲     │  ← arc: night → window → sunrise,
│      │  ●━━━━━[████]━━━☀   │     │    dot = now
│        ╲                  ╱      │
│                                  │
│   BRAHMA MUHURTA TOMORROW        │
│       04:58 – 05:46 AM           │
│   Sunrise 06:34 · in 7h 12m      │
│                                  │
│  ┌────────────────────────────┐  │
│  │ Adaptive alarm      [ ON ●]│  │
│  │ Gentle · 7 stages · Bell   │  │
│  │ Mon Tue Wed Thu Fri Sat Sun│  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ Wake streak  🔥 12 days     │  │
│  │ This week: ● ● ● ○ ● ● –   │  │
│  └────────────────────────────┘  │
│   [ Skip tomorrow ]  [ Edit ]    │
├──────────────────────────────────┤
│ 🌅    ⏰     🕐     ⏳     ⏱      │
└──────────────────────────────────┘
```

### 5.2 Adaptive alarm settings (from "Edit")
```
┌──────────────────────────────────┐
│ ←  Brahma Alarm                  │
│ Mode   (●Gentle) (Standard) (Custom)
│ Window offset   96 min ▾         │
│ Stages          7      [−] [+]   │
│ ┌ Timeline preview ────────────┐ │
│ │ 04:58 ▁ 05:06 ▂ 05:14 ▃ ...  │ │
│ │                05:46 █ FULL  │ │
│ └──────────────────────────────┘ │
│ Sound           Temple bell  ▶   │
│ Final sound     Conch        ▶   │
│ Vibration       From stage 3 ▾   │
│ Repeat          M T W T F S S    │
│ Backup time     05:00 (if no     │
│                 sunrise/location)│
│ Location        Auto (GPS) ▾     │
└──────────────────────────────────┘
```

### 5.3 Ringing screen (full‑screen, over lock screen)
```
┌──────────────────────────────────┐
│        (dawn gradient)           │
│                                  │
│            05:14                 │
│      Brahma Muhurta · stage 3/7  │
│   "Utho, jaago" / short quote    │
│                                  │
│   Sunrise in 1h 20m              │
│                                  │
│    ┌──────────────────────┐      │
│    │   ☀  I'm awake        │      │  ← swipe/hold to dismiss all stages
│    └──────────────────────┘      │
│          Snooze → next stage     │
└──────────────────────────────────┘
```

### 5.4 Alarm (normal alarms)
```
┌──────────────────────────────────┐
│ Alarms                       ＋   │
│ Next alarm in 7h 12m             │
│ ┌────────────────────────────┐   │
│ │ 06:30 AM          [ ● ]    │   │
│ │ Gym · M T W T F            │   │
│ └────────────────────────────┘   │
│ ┌────────────────────────────┐   │
│ │ 08:00 AM          [ ○ ]    │   │
│ │ Once                       │   │
│ └────────────────────────────┘   │
└──────────────────────────────────┘
Edit sheet: time wheel, label, repeat days, sound, vibration,
snooze length, gradual volume, delete.
```

### 5.5 Clock (world clock)
```
┌──────────────────────────────────┐
│         10:42:15 PM              │
│       Sat, 25 October            │
│ ─────────────────────────────    │
│ London       5:12 PM   −5:30     │
│ New York    12:12 PM   −10:30    │
│                              ＋   │
└──────────────────────────────────┘
```

### 5.6 Timer
Large circular progress ring, number pad input, presets (1/5/10/25 min),
multiple simultaneous timers, +1 min button, notification with controls.

### 5.7 Stopwatch
Big readout `00:12.48`, Start/Pause, Lap/Reset, lap list with best/worst
lap highlighted; runs in a foreground notification when app is closed.

### 5.8 Settings
Theme (Dark / AMOLED / Light / System), 12/24‑hour, Brahma offset, default
snooze, volume button behaviour, week start day, location source,
"Battery optimisation" helper (opens system screen so OEMs don't kill alarms).

### 5.9 First‑run onboarding (3 screens)
1. What is Brahma Muhurta (one paragraph + dawn illustration)
2. Location permission (with "enter city manually" option)
3. Notification + full‑screen alarm permission → shows tomorrow's window

---

## 6. Project structure

```
core/                         pure Kotlin, unit tested on the JVM
  SunCalculator.kt            NOAA sunrise
  Brahma.kt                   window, stages, next plan, sleep reminder, streak
  AlarmMath.kt                next occurrence of a normal alarm
  Cities.kt                   offline city list
app/src/main/kotlin/com/brahmclock/
  BrahmApp.kt                 Application, notification channels
  data/Repo.kt                SharedPreferences-backed state
  alarm/Scheduler.kt          AlarmManager (setAlarmClock)
  alarm/AlarmReceiver.kt      ring / sleep-reminder broadcasts
  alarm/RingService.kt        foreground playback with fade-in
  alarm/RingActivity.kt       full-screen lock-screen UI
  alarm/BootReceiver.kt       reschedule on boot/time change
  location/LocationHelper.kt  platform LocationManager
  ui/                         Brahma, Alarms, Clock, Timer, Stopwatch, Settings
```

## 7. Build roadmap

| Phase | Deliverable |
|---|---|
| **0. Setup** | Gradle project, Compose theme, bottom nav skeleton, CI build |
| **1. Sun engine** | `SunCalculator` + `BrahmaWindow` with unit tests vs. known sunrise tables |
| **2. Location** | Permission flow, LocationManager, manual city fallback, caching |
| **3. Brahma home UI** | Arc visual, today/tomorrow window, toggle |
| **4. Alarm engine** | Scheduler, receivers, ring service, full‑screen activity, staged pulses, reschedule on boot/time change |
| **5. Normal alarms** | List, editor, repeat, snooze |
| **6. Clock / Timer / Stopwatch** | The three standard tabs |
| **7. Polish** | Onboarding, settings, streaks, sound pack, OEM battery helper, release build with R8 |

Testing focus: sun math (unit), stage planner (unit), scheduling across
DST/timezone changes (instrumented), real‑device overnight test.

---

## 8. Decisions

- Offset: 96 min (traditional), adjustable.
- Min Android: 8.0 (API 26).
- Language: English only.
- Sounds: none bundled; user can pick from their own library.
- Extra: sleep reminder 8 h before the window.
- Name / package: Brahm Clock / `com.brahmclock`.
